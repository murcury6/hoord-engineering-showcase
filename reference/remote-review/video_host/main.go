package main

import (
	"context"
	"crypto/tls"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"tailscale.com/tsnet"
)

func writeStatus(state, stage, base, auth, problem string, ready bool, owner int) {
	value := map[string]any{"stage": stage, "base_url": base, "auth_url": auth, "error": problem, "ready": ready, "pid": os.Getpid(), "owner_pid": owner, "updated": time.Now().UTC().Format(time.RFC3339)}
	data, _ := json.MarshalIndent(value, "", "  ")
	path := filepath.Join(state, "video-host.json")
	if err := os.WriteFile(path+".new", data, 0600); err != nil {
		log.Print(err)
		return
	}
	if err := os.Rename(path+".new", path); err != nil {
		log.Print(err)
	}
	escape := strings.NewReplacer("\\", "\\\\", "\n", "\\n", "\r", "\\r")
	props := fmt.Sprintf("stage=%s\nbase_url=%s\nauth_url=%s\nerror=%s\nready=%t\n", escape.Replace(stage), base, auth, escape.Replace(problem), ready)
	if os.WriteFile(filepath.Join(state, "video-host.properties.new"), []byte(props), 0600) == nil {
		os.Rename(filepath.Join(state, "video-host.properties.new"), filepath.Join(state, "video-host.properties"))
	}
}

func run(state string, owner int) error {
	release, err := singleInstance(state)
	if err != nil {
		return err
	}
	defer release()
	defer writeStatus(state, "stopped", "", "", "Dev Panel closed", false, owner)
	s := &tsnet.Server{Dir: filepath.Join(state, "tailscale"), Hostname: "hoord-videos", UserLogf: log.Printf}
	defer s.Close()
	lc, err := s.LocalClient()
	if err != nil {
		return err
	}
	var server *http.Server
	defer func() {
		if server != nil {
			server.Close()
		}
	}()
	base := ""
	for ownerAlive(owner) {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		st, e := lc.Status(ctx)
		cancel()
		if e != nil {
			writeStatus(state, "connecting", base, "", e.Error(), false, owner)
			time.Sleep(3 * time.Second)
			continue
		}
		if st.BackendState != "Running" || st.Self == nil {
			writeStatus(state, "sign in to Tailscale", base, st.AuthURL, st.BackendState, false, owner)
		} else {
			host := strings.TrimSuffix(st.Self.DNSName, ".")
			if !strings.HasSuffix(host, ".ts.net") || st.Self.UserID == 0 {
				return fmt.Errorf("A personal Tailscale login and MagicDNS are required")
			}
			base = "https://" + host
			if server == nil {
				// Check certificates before telling Sheets this host is ready.
				_, e = lc.GetCertificate(&tls.ClientHelloInfo{ServerName: host})
				if e != nil {
					writeStatus(state, "enable Tailscale HTTPS", base, "", e.Error(), false, owner)
					time.Sleep(5 * time.Second)
					continue
				}
				ln, e := s.ListenTLS("tcp", ":443")
				if e != nil {
					return e
				}
				ownerID := st.Self.UserID
				server = &http.Server{ReadHeaderTimeout: 10 * time.Second, IdleTimeout: 30 * time.Second, Handler: videoHandler(state, func(r *http.Request) bool {
					ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
					defer cancel()
					who, err := lc.WhoIs(ctx, r.RemoteAddr)
					return err == nil && who.UserProfile != nil && who.UserProfile.ID == ownerID && who.Node != nil && len(who.Node.Tags) == 0
				})}
				go func() {
					if e := server.Serve(ln); e != nil && e != http.ErrServerClosed {
						log.Print(e)
					}
				}()
				go verifyVideo(s, state, base)
			}
			writeStatus(state, "ready", base, "", "", true, owner)
		}
		time.Sleep(3 * time.Second)
	}
	return nil
}

// Exercise HTTPS, tailnet identity and byte ranges against a real recorded clip.
func verifyVideo(s *tsnet.Server, state, base string) {
	result := map[string]any{"updated": time.Now().UTC().Format(time.RFC3339), "ok": false}
	defer func() {
		data, _ := json.MarshalIndent(result, "", "  ")
		os.WriteFile(filepath.Join(state, "video-host-verification.json"), data, 0600)
	}()
	raw, err := os.ReadFile(filepath.Join(state, "items.json"))
	if err != nil {
		result["error"] = err.Error()
		return
	}
	var items []entry
	if json.Unmarshal(raw, &items) != nil {
		return
	}
	for _, item := range items {
		if !validPreview(item.Preview, item.Digest) {
			continue
		}
		client := s.HTTPClient()
		client.Timeout = 20 * time.Second
		req, _ := http.NewRequest("GET", base+"/api/video/"+item.ID, nil)
		req.Header.Set("Range", "bytes=0-63")
		res, err := client.Do(req)
		if err != nil {
			result["error"] = err.Error()
			return
		}
		defer res.Body.Close()
		data, err := io.ReadAll(io.LimitReader(res.Body, 65))
		result["id"] = item.ID
		result["status"] = res.StatusCode
		result["bytes"] = len(data)
		result["ok"] = err == nil && res.StatusCode == 206 && len(data) == 64 && res.TLS != nil && len(res.TLS.VerifiedChains) > 0
		return
	}
	result["error"] = "No complete clips yet"
}
func main() {
	state := flag.String("state", "", "Protected remote-review state directory")
	owner := flag.Int("owner-pid", 0, "Dev Panel process ID")
	flag.Parse()
	abs, err := filepath.Abs(*state)
	if err != nil || *state == "" || !ownerAlive(*owner) {
		log.Fatal("A running Dev Panel and state directory are required")
	}
	if err = run(abs, *owner); err != nil {
		writeStatus(abs, "error", "", "", err.Error(), false, *owner)
		log.Fatal(err)
	}
}
