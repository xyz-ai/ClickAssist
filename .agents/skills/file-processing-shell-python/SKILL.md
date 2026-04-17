---
name: file-processing-shell-python
description: Prefer Shell or Python for file-processing tasks such as reading, writing, filtering, merging, parsing, renaming, extracting text, format conversion, data cleaning, directory traversal, and log analysis. Use shell first for simple operations and Python for structured or multi-step processing. Execute commands and show real results instead of only describing steps.
---

# File Processing: Shell / Python First

Use this skill when the task is primarily about files, text streams, structured data, or batch processing.

Examples:
- read / write / convert files
- filter or transform text
- rename or move many files
- parse CSV / JSON / XML
- clean data
- traverse directories
- analyze logs
- merge or split files
- extract text from many files

## Core rule
When the task is a file-processing task, prefer executable commands or scripts over natural-language-only instructions.

Do not stop at explaining what to do if the environment allows actual execution.

## Tool preference

### 1. Shell one-liners first
Use shell for simple file and text tasks:
- grep
- sed
- awk
- cut
- sort
- uniq
- tr
- xargs
- find

### 2. Python for structured or multi-step logic
Use Python when:
- parsing CSV / JSON / XML
- handling nested structures
- coordinating multiple steps
- doing non-trivial validation
- producing transformed output files
- implementing logic that would become hard to read in shell

Preferred standard libraries:
- pathlib
- csv
- json
- re
- xml.etree.ElementTree

Use pandas only if clearly helpful.

### 3. Combine both when helpful
Use shell for discovery / orchestration and Python for core transformation when that is cleaner.

## Execution flow
1. Inspect inputs first
   - use `ls`, `find`, `head`, `tail`, `file`, or similar tools

2. Run the smallest useful command first
   - prefer a short command that proves the approach

3. Use scripts when complexity grows
   - save longer scripts inside this skill's `scripts/` directory or another temporary workspace path inside the project

4. Run the script and inspect output
   - show the real output or summarize it concisely

5. If execution fails, read the actual error
   - fix the root cause
   - retry with the corrected command or script

## Output style
- provide the command or script
- keep comments short and useful
- run it when the environment allows
- show the actual output or summarize the result
- explain the result in 1–2 concise sentences

## Do not
- do not answer a file-processing request with only high-level prose if execution is available
- do not use Python if a simple shell pipeline is clearer
- do not force shell for structured parsing when Python is clearly cleaner
- do not modify unrelated files while processing target files