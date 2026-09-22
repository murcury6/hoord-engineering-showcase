package main

import (
	"crypto/sha256"
	"fmt"
	"golang.org/x/sys/windows"
)

func ownerAlive(pid int) bool {
	if pid <= 0 {
		return false
	}
	h, err := windows.OpenProcess(windows.PROCESS_QUERY_LIMITED_INFORMATION, false, uint32(pid))
	if err != nil {
		return false
	}
	defer windows.CloseHandle(h)
	var code uint32
	return windows.GetExitCodeProcess(h, &code) == nil && code == 259
}
func singleInstance(state string) (func(), error) {
	name, _ := windows.UTF16PtrFromString(fmt.Sprintf(`Local\HoordVideos-%x`, sha256.Sum256([]byte(state))))
	h, err := windows.CreateMutex(nil, false, name)
	if err != nil {
		if h != 0 {
			windows.CloseHandle(h)
		}
		return nil, err
	}
	return func() { windows.CloseHandle(h) }, nil
}
