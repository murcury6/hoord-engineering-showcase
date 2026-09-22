package main

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func fixture(t *testing.T) (string, entry) {
	t.Helper()
	dir := t.TempDir()
	p := preview{Digest: strings.Repeat("a", 64), Complete: true, Frames: 150, FPS: 60, Seconds: 2.5}
	e := entry{ID: "W-0001", Round: "round-one", Digest: p.Digest, State: "ready", Preview: p, Video: filepath.Join(dir, "previews", "round-one", "animation.mp4")}
	os.MkdirAll(filepath.Dir(e.Video), 0700)
	os.WriteFile(e.Video, []byte("0123456789"), 0600)
	raw, _ := json.Marshal(p)
	os.WriteFile(filepath.Join(filepath.Dir(e.Video), "preview.json"), raw, 0600)
	saveItems(t, dir, []entry{e})
	return dir, e
}
func saveItems(t *testing.T, dir string, entries []entry) {
	t.Helper()
	raw, _ := json.Marshal(entries)
	if err := os.WriteFile(filepath.Join(dir, "items.json"), raw, 0600); err != nil {
		t.Fatal(err)
	}
}
func response(h http.Handler, method, path, span string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, path, nil)
	if span != "" {
		r.Header.Set("Range", span)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}
func TestPrivateReadOnlyRangeAndHead(t *testing.T) {
	dir, _ := fixture(t)
	deny := videoHandler(dir, func(*http.Request) bool { return false })
	if w := response(deny, "GET", "/api/video/W-0001", ""); w.Code != 403 {
		t.Fatal(w.Code)
	}
	h := videoHandler(dir, func(*http.Request) bool { return true })
	if w := response(h, "GET", "/api/video/W-0001", "bytes=6-9"); w.Code != 206 || w.Body.String() != "6789" || w.Header().Get("Content-Range") != "bytes 6-9/10" {
		t.Fatal(w)
	}
	if w := response(h, "HEAD", "/api/video/W-0001", ""); w.Code != 200 || w.Body.Len() != 0 || w.Header().Get("Content-Length") != "10" {
		t.Fatal(w)
	}
	if w := response(h, "POST", "/api/video/W-0001", ""); w.Code != 405 {
		t.Fatal(w.Code)
	}
	for _, path := range []string{"/", "/api/video/../items.json", "/api/video/W-0001?path=secret", "/api/video/W-9999"} {
		if w := response(h, "GET", path, ""); w.Code != 404 {
			t.Fatal(path, w.Code)
		}
	}
}
func TestInvalidEvidenceAndEscapingPathsNeverServed(t *testing.T) {
	for _, kind := range []string{"truncated", "digest", "duration", "path", "duplicate", "disk evidence"} {
		t.Run(kind, func(t *testing.T) {
			dir, e := fixture(t)
			switch kind {
			case "truncated":
				e.Preview.Truncated = true
			case "digest":
				e.Digest = strings.Repeat("b", 64)
			case "duration":
				e.Preview.Seconds = 1
			case "path":
				e.Video = filepath.Join(dir, "secret.mp4")
			case "disk evidence":
				os.WriteFile(filepath.Join(filepath.Dir(e.Video), "preview.json"), []byte(`{}`), 0600)
			}
			entries := []entry{e}
			if kind == "duplicate" {
				entries = append(entries, e)
			}
			saveItems(t, dir, entries)
			w := response(videoHandler(dir, func(*http.Request) bool { return true }), "GET", "/api/video/W-0001", "")
			if w.Code < 400 {
				t.Fatal(w.Code)
			}
		})
	}
}
