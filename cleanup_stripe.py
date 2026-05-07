"""
Cleanup script: removes leftover old synchronous side-effects code from stripe_integration.py.
The new async closure is at lines 612-1161. Lines after the new return (1161) up to the next
function definition (def process_webhook_event) are old duplicates that must be deleted.
"""
import re

FILE = r"C:\Users\kochn\.cursor\Medidesk\Event_orders_portal_monorepo\backend\stripe_integration.py"

with open(FILE, "r", encoding="utf-8") as f:
    lines = f.readlines()

# Find the new return closing brace line (contains "recovery_reason" on the next line approach)
# Actually, find the line with `    }` that is on its own after `"recovery_reason": recovery_reason or None,`
# Then find the next `def process_webhook_event` line.

# Strategy: scan for the pattern:
# Line containing: "recovery_reason": recovery_reason or None,
# Next non-empty line: }
# Then delete everything from after that } until (but not including) `def process_webhook_event`

new_return_end = None
next_def_start = None

for i, line in enumerate(lines):
    stripped = line.strip()
    # Find the FIRST occurrence of the async return's closing brace after line 1140
    if i >= 1140 and stripped == '}' and new_return_end is None:
        # Check if previous non-blank line has "recovery_reason"
        for j in range(i-1, max(i-5, 0), -1):
            if 'recovery_reason' in lines[j]:
                new_return_end = i  # 0-indexed
                break
        if new_return_end is not None:
            break

if new_return_end is None:
    print("ERROR: Could not find new return block end")
    exit(1)

print(f"Found new return closing brace at line {new_return_end + 1}")

# Now find `def process_webhook_event` after new_return_end
for i in range(new_return_end + 1, len(lines)):
    if lines[i].startswith('def process_webhook_event'):
        next_def_start = i
        break

if next_def_start is None:
    print("ERROR: Could not find def process_webhook_event")
    exit(1)

print(f"Found def process_webhook_event at line {next_def_start + 1}")

# Everything between new_return_end+1 and next_def_start should be deleted
# (except we want to keep two blank lines between the } and def)
lines_to_delete = list(range(new_return_end + 1, next_def_start))
print(f"Will delete {len(lines_to_delete)} lines ({new_return_end + 2} to {next_def_start})")

# Build new file
new_lines = lines[:new_return_end + 1]  # Up to and including the }
new_lines.append("\n")
new_lines.append("\n")
new_lines.extend(lines[next_def_start:])  # From def process_webhook_event onwards

with open(FILE, "w", encoding="utf-8") as f:
    f.writelines(new_lines)

print(f"SUCCESS: Removed {len(lines_to_delete)} old lines. New file has {len(new_lines)} lines.")
