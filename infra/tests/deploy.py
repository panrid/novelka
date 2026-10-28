import json
import os
from pathlib import Path
import subprocess
import tempfile

# The production script requires root. All cluster, runtime and HTTP commands below
# are replaced with local fixtures: this test never contacts or changes a cluster.
if os.geteuid() != 0:
    raise SystemExit('Run with sudo python3 infra/tests/deploy.py (all external commands are mocked).')
SCRIPT = Path(__file__).resolve().parents[1] / 'deploy.sh'
SHA = '1' * 40
STUB = r'''#!/usr/bin/python3
import json,os,sys
from pathlib import Path
name=Path(sys.argv[0]).name
args=sys.argv[1:]
scenario=os.environ['SCENARIO']
with open(os.environ['COMMAND_LOG'],'a') as f:
    f.write(json.dumps([name,*args])+'\n')
if name=='kubectl':
    if args[:2]==['-n','novelka-prod']: args=args[2:]
    if args[:2]==['get','nodes']: print('{"items":[{}]}')
    elif args[:2]==['get','deployment']:
        print('{"metadata":{"annotations":{"deployment.kubernetes.io/revision":"2"}}}')
    elif args[:2]==['get','pvc']: print('{"status":{"phase":"Bound"}}')
    elif args[:1]==['patch'] and '--dry-run=server' not in args and scenario=='patch-failure': sys.exit(1)
    elif args[:2]==['rollout','status'] and scenario=='rollout-failure':
        marker=Path(os.environ['STATE_DIR'])/'rollout-failed'
        if not marker.exists(): marker.touch(); sys.exit(1)
    elif args[:1]==['exec']:
        command=args[args.index('--')+1:]
        if command[:1]==['tar']: sys.stdin.buffer.read()
        elif command[:1]==['cat']: sys.stdout.buffer.write(b'fake-image')
        elif command[:1]==['buildctl-daemonless.sh'] and scenario=='build-failure': sys.exit(1)
elif name=='k3s':
    state=Path(os.environ['STATE_DIR'])/'image'
    if 'import' in args: state.touch()
    elif 'ls' in args and (state.exists() or scenario=='cached'):
        print('docker.io/library/novelka:'+os.environ['REVISION'])
elif name=='curl':
    if scenario=='public-failure': sys.exit(22)
    if '-o' not in args: print('{"status":"ok"}')
'''

for scenario in ['success','cached','build-failure','rollout-failure','public-failure','patch-failure','check','build-only']:
    with tempfile.TemporaryDirectory() as directory:
        root=Path(directory)
        binaries=root/'bin'; binaries.mkdir()
        for name in ['kubectl','k3s','curl']:
            path=binaries/name; path.write_text(STUB); path.chmod(0o755)
        release=root/'release'; (release/'infra/k8s').mkdir(parents=True)
        for file in ['novelka.jar','Dockerfile','infra/k8s/buildkit.yaml','infra/k8s/deployment.patch.yaml']:
            (release/file).write_text('fixture')
        log=root/'commands.jsonl'
        env=dict(os.environ,PATH=f'{binaries}:'+os.environ['PATH'],SCENARIO=scenario,
                 STATE_DIR=directory,COMMAND_LOG=str(log),REVISION=SHA)
        arguments=['bash',str(SCRIPT),str(release),SHA]
        if scenario in ['check','build-only']: arguments.append('--'+scenario)
        result=subprocess.run(arguments,env=env,capture_output=True,text=True,timeout=15)
        commands=[json.loads(line) for line in log.read_text().splitlines()]
        mutated=any(c[0]=='kubectl' and 'patch' in c and '--dry-run=server' not in c for c in commands)
        rollback=any(c[0]=='kubectl' and 'undo' in c for c in commands)
        built=any('buildctl-daemonless.sh' in c for c in commands)
        deleted=any(c[0]=='kubectl' and 'delete' in c for c in commands)
        if scenario in ['success','cached','check','build-only']:
            assert result.returncode==0,(scenario,result.stdout,result.stderr)
            assert not rollback,scenario
        else:
            assert result.returncode!=0,scenario
        if scenario in ['build-failure','check','build-only']: assert not mutated,scenario
        if scenario in ['rollout-failure','public-failure','patch-failure']:
            assert rollback,scenario
            assert any('--to-revision=2' in c for c in commands),scenario
        if scenario=='build-failure': assert deleted and not rollback,scenario
        if scenario=='cached': assert not built,scenario
        if scenario=='success': assert built and deleted and mutated,scenario
        if scenario=='check': assert not built and not deleted,scenario
        print('PASS',scenario)
