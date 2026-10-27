#!/usr/bin/env bash
set -e

if [ "$#" -ne 1 ]; then
echo "Usage: $0 "
exit 1
fi

INPUT_FILE="$1"

if [ ! -f "$INPUT_FILE" ]; then
echo "Error: File '$INPUT_FILE' not found."
exit 1
fi

# Extract the Subject line (removes any leading 'Subject: [PATCH] ' prefix if present)
SUBJECT=$(grep -m1 '^Subject:' "$INPUT_FILE" | sed -E 's/^Subject: ( $$PATCH[^]]*$$ )?//')

# Fallback in case no Subject header exists
if [ -z "$SUBJECT" ]; then
SUBJECT="Evols patch"
fi

# Temporary file to store the cleaned content safely
TEMP_FILE=$(mktemp)

# Write the Subject line at the top
echo "$SUBJECT" > "$TEMP_FILE"

# Add space after subject
echo "" >> "$TEMP_FILE"

awk '/^---$/ {p=1; next} p' "$INPUT_FILE" >> "$TEMP_FILE"

# apply patch
git apply "$TEMP_FILE"

# Overwrite original file
# mv "$TEMP_FILE" "$INPUT_FILE"
