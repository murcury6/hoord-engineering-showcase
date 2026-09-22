package main

import (
	"encoding/json"
	"math"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var entryPath = regexp.MustCompile(`^/api/video/(W-[0-9]{4,})$`)
var roundName = regexp.MustCompile(`^round-[\w-]+$`)
var digestPattern = regexp.MustCompile(`^[a-f0-9]{64}$`)

type preview struct {
	Digest    string  `json:"digest"`
	Complete  bool    `json:"complete"`
	Truncated bool    `json:"truncated"`
	Frames    int     `json:"frame_count"`
	FPS       int     `json:"fps"`
	Seconds   float64 `json:"seconds"`
}
type entry struct {
	ID      string  `json:"id"`
	Round   string  `json:"round"`
	Digest  string  `json:"digest"`
	Video   string  `json:"video"`
	State   string  `json:"state"`
	Preview preview `json:"preview"`
}

func validPreview(p preview, digest string) bool {
	return digestPattern.MatchString(digest) && p.Digest == digest && p.Complete && !p.Truncated && p.Frames > 0 && (p.FPS == 30 || p.FPS == 60) && p.Seconds > 0 && p.Seconds <= 600 && math.Abs(float64(p.Frames)/float64(p.FPS)-p.Seconds) < .0001
}

// Authorization runs before looking up IDs or touching any video files.
func videoHandler(state string, authorized func(*http.Request) bool) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Cache-Control", "private, no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'none'; media-src 'self'; frame-ancestors 'none'")
		if !authorized(r) {
			http.Error(w, "Access denied", http.StatusForbidden)
			return
		}
		if r.Method != "GET" && r.Method != "HEAD" {
			w.Header().Set("Allow", "GET, HEAD")
			http.Error(w, "Read only", 405)
			return
		}
		match := entryPath.FindStringSubmatch(r.URL.Path)
		if match == nil || r.URL.RawQuery != "" {
			http.NotFound(w, r)
			return
		}
		raw, err := os.ReadFile(filepath.Join(state, "items.json"))
		if err != nil {
			http.Error(w, "Queue unavailable", 503)
			return
		}
		var items []entry
		if json.Unmarshal(raw, &items) != nil {
			http.Error(w, "Queue unavailable", 503)
			return
		}
		var selected *entry
		for i := range items {
			if items[i].ID == match[1] {
				if selected != nil {
					http.Error(w, "Conflicting identity", 409)
					return
				}
				selected = &items[i]
			}
		}
		if selected == nil || !roundName.MatchString(selected.Round) || !validPreview(selected.Preview, selected.Digest) {
			http.NotFound(w, r)
			return
		}
		// Derive the only allowed path; never serve an arbitrary path from queue JSON.
		expected := filepath.Join(state, "previews", selected.Round, "animation.mp4")
		if !strings.EqualFold(filepath.Clean(selected.Video), expected) {
			http.NotFound(w, r)
			return
		}
		resolved, err := filepath.EvalSymlinks(expected)
		if err != nil || !strings.EqualFold(resolved, expected) {
			http.NotFound(w, r)
			return
		}
		evidence, err := os.ReadFile(filepath.Join(filepath.Dir(expected), "preview.json"))
		var p preview
		if err != nil || json.Unmarshal(evidence, &p) != nil || !validPreview(p, selected.Digest) || p.Frames != selected.Preview.Frames || p.FPS != selected.Preview.FPS {
			http.NotFound(w, r)
			return
		}
		f, err := os.Open(expected)
		if err != nil {
			http.NotFound(w, r)
			return
		}
		defer f.Close()
		info, err := f.Stat()
		if err != nil || !info.Mode().IsRegular() || info.Size() == 0 {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "video/mp4")
		w.Header().Set("Content-Disposition", `inline; filename="`+selected.ID+`.mp4"`)
		// ServeContent implements byte ranges and HEAD, including seeking the entire clip.
		http.ServeContent(w, r, selected.ID+".mp4", info.ModTime(), f)
	})
}
