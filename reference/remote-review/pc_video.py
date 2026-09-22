"""Private, read-only videos on the PC; no cloud video upload transport."""
import re
import subprocess
import time
from pathlib import Path

HOST = re.compile(r'https://[a-z0-9-]+\.[a-z0-9-]+\.ts\.net')

def base_url(worker):
    status=worker.read(worker.STATE/'video-host.json',{})
    base=status.get('base_url','')
    if not base:return ''
    if not HOST.fullmatch(base):raise ValueError('Invalid private PC video host')
    return base

def start(worker, owner_pid):
    current=worker.read(worker.STATE/'video-host.json',{})
    if current.get('owner_pid')==owner_pid and worker.owner_alive(current.get('pid',0)):return
    exe=worker.WORKSPACE/'work/tools/hoord-video-host.exe'
    if not exe.is_file():raise RuntimeError('Build the private PC video server first; see GOOGLE_SHEETS_SETUP.md')
    with (worker.STATE/'video-host.log').open('a',encoding='utf-8') as log:
        process=subprocess.Popen([str(exe),'--state',str(worker.STATE),'--owner-pid',str(owner_pid)],
                                 stdout=log,stderr=log,cwd=worker.WORKSPACE,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    # Avoid publishing a temporary empty host while the saved node reconnects.
    deadline=time.monotonic()+12
    while process.poll() is None and time.monotonic()<deadline:
        current=worker.read(worker.STATE/'video-host.json',{})
        if current.get('pid')==process.pid and (current.get('base_url') or current.get('auth_url')):break
        time.sleep(.25)

def video_url(worker,item):
    base=base_url(worker)
    if not base:raise ValueError('Sign in to Tailscale to connect PC videos')
    if not re.fullmatch(r'W-\d{4,}',item['id']) or not re.fullmatch(r'round-[\w-]+',item['round']):raise ValueError('Invalid video identity')
    expected=worker.STATE/'previews'/item['round']/'animation.mp4'
    if Path(item['video']).resolve()!=expected.resolve() or not expected.is_file():raise ValueError('Animation must stay in the PC preview folder')
    p=item['preview'];disk=worker.read(expected.with_name('preview.json'),{})
    for evidence in (p,disk):
        if evidence.get('digest')!=item['digest'] or not evidence.get('complete') or evidence.get('truncated'):
            raise ValueError('Full animation identity evidence required')
        fps=evidence.get('fps');frames=evidence.get('frame_count',0);seconds=evidence.get('seconds',0)
        if fps not in (30,60) or frames<1 or not 0<seconds<=600 or abs(frames/fps-seconds)>.0001:
            raise ValueError('Full animation duration evidence required')
    if p['frame_count']!=disk['frame_count'] or p['fps']!=disk['fps']:raise ValueError('Animation evidence changed')
    return base+'/api/video/'+item['id']
