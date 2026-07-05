#!/usr/bin/env python3
"""
test-platform.py — Cross-platform Python script for testing SCRIPT tasks
Works on Unix/Linux/macOS (python3) and Windows (python)
Usage: python3 test-platform.py --message "Hello"
"""

import sys
import os
import platform
import json
from datetime import datetime

def main():
    # Parse arguments
    message = "Default message"
    if "--message" in sys.argv:
        idx = sys.argv.index("--message")
        if idx + 1 < len(sys.argv):
            message = sys.argv[idx + 1]

    # Gather system information
    info = {
        "message": message,
        "timestamp": datetime.now().isoformat(),
        "platform": platform.system(),
        "platform_release": platform.release(),
        "platform_version": platform.version(),
        "architecture": platform.machine(),
        "python_version": platform.python_version(),
        "working_directory": os.getcwd(),
        "script_path": os.path.abspath(__file__),
        "arguments": sys.argv[1:],
    }

    # Print human-readable output
    print(f"🐍 Python Cross-Platform Test")
    print(f"Platform: {info['platform']} {info['platform_release']}")
    print(f"Python: {info['python_version']}")
    print(f"Message: {info['message']}")
    print(f"Working directory: {info['working_directory']}")
    print(f"Arguments: {info['arguments']}")
    print()

    # Print JSON output for task result parsing
    print("JSON Output:")
    print(json.dumps(info, indent=2))

    return 0

if __name__ == "__main__":
    sys.exit(main())

