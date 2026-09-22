"""Synthetic storage/metrics contract for transport tests, not the Hoord engine."""
import datetime
import json
from pathlib import Path

STATE=Path('data')

def read(path,default=None):
    return json.loads(path.read_text(encoding='utf-8')) if path.exists() else default

def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value),encoding='utf-8')

def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()

def generation_timings(items):
    return {}

def generation_stats(items,timings):
    return {'total':0,'failed':0,'average_seconds':None}

def site_request(*args,**kwargs):
    raise AssertionError('Transport tests must never upload to a Site')
