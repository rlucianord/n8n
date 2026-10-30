from __future__ import annotations

import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path


def memory_gib() -> float | None:
    if os.name == "nt":
        try:
            import ctypes
            from ctypes import wintypes

            class MEMORYSTATUSEX(ctypes.Structure):
                _fields_ = [
                    ("dwLength", wintypes.DWORD),
                    ("dwMemoryLoad", wintypes.DWORD),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]

            status = MEMORYSTATUSEX()
            status.dwLength = ctypes.sizeof(status)
            if ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status)):
                return round(status.ullTotalPhys / (1024 ** 3), 2)
        except (AttributeError, OSError):
            return None
    try:
        pages = os.sysconf("SC_PHYS_PAGES")
        page_size = os.sysconf("SC_PAGE_SIZE")
        return round(pages * page_size / (1024 ** 3), 2)
    except (ValueError, OSError, AttributeError):
        return None


def gpu_report() -> list[str]:
    try:
        result = subprocess.run(
            ["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader"],
            capture_output=True,
            text=True,
            timeout=5,
            check=False,
        )
        if result.returncode == 0 and result.stdout.strip():
            return [line.strip() for line in result.stdout.splitlines()]
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError):
        pass
    return ["No NVIDIA GPU detected by nvidia-smi (other GPUs may still be present)."]


def main() -> None:
    print("=== MI DOBLE: HARDWARE DIAGNOSTIC ===")
    print(f"OS: {platform.platform()}")
    print(f"Architecture: {platform.machine()}")
    print(f"CPU: {platform.processor() or platform.machine()}")
    print(f"Logical CPU cores: {os.cpu_count() or 'unknown'}")
    ram = memory_gib()
    print(f"Physical RAM: {ram if ram is not None else 'unknown'} GiB")
    print("GPU:")
    for line in gpu_report():
        print(f"  {line}")
    print(f"Python: {sys.version.split()[0]}")
    try:
        import torch
        print(f"PyTorch: {torch.__version__}")
        print(f"CUDA available: {torch.cuda.is_available()}")
        if torch.cuda.is_available():
            for index in range(torch.cuda.device_count()):
                props = torch.cuda.get_device_properties(index)
                print(
                    f"  GPU {index}: {props.name}; VRAM: "
                    f"{round(props.total_memory / (1024 ** 3), 2)} GiB"
                )
    except ImportError:
        print("PyTorch: not installed (not required for this diagnostic).")
    try:
        usage = shutil.disk_usage(Path.home())
        print(f"Free disk space: {round(usage.free / (1024 ** 3), 2)} GiB")
    except OSError:
        print("Free disk space: unknown")
    print("====================================")
    print("Run this diagnostic on the target PC, not in Codespaces.")


if __name__ == "__main__":
    main()
