#!/usr/bin/env python3
"""Export private debug-app diagnostics without clearing or changing the app."""
import datetime
import pathlib
import subprocess
import sys

output = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else pathlib.Path('captures') / datetime.datetime.now().strftime('diagnostics-%Y%m%d-%H%M%S.tar')
output.parent.mkdir(parents=True, exist_ok=True)
result = subprocess.run(['adb', 'exec-out', 'run-as', 'com.ronnie.teslakey', 'tar', '-cf', '-', 'files/diagnostics'], capture_output=True)
if result.returncode or not result.stdout or result.stdout.startswith(b'run-as:'):
    sys.exit((result.stderr or result.stdout).decode(errors='replace'))
output.write_bytes(result.stdout)
print(output.resolve())
