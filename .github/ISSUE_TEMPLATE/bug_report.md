---
name: Bug report
about: Report a defect in Claude Code Native
title: "[BUG] "
labels: bug
assignees: ''
---

> Please **do not** include API keys, OAuth tokens, conversation transcripts,
> source code under NDA, or absolute paths that reveal personal information.
> Redact aggressively before posting.

## Description

A clear and concise description of what the bug is.

## Steps to reproduce

1. Open IDE on project `…`
2. Start a new Claude chat
3. Type `…`
4. Observe `…`

## Expected behaviour

What you expected to happen.

## Observed behaviour

What actually happened. Include error messages verbatim.

## Environment

> From **6.5.0** the minimum supported IDE is **2026.2 (build 262.8665.258)**. On 2025.3 or 2026.1 the
> last supported version is **6.0.1**; on 2025.1 or 2025.2 it is **5.1.1**. A bug report against 6.5.0 on
> an older build is expected behaviour, not a defect. In Remote Development, say whether the plugin is
> installed on the host, the client, or both.

- **OS:** (e.g. Ubuntu 24.04, macOS 14.5, Windows 11 23H2)
- **IDE:** (Help → About → product + build, e.g. `IntelliJ IDEA 2026.2.3 IU-262.10968.63`)
- **Plugin version:** (Settings → Plugins → Claude Code Native)
- **`claude` binary version:** output of `claude --version`
- **Binary location:** `which claude` (Linux/macOS) or `where claude` (Windows)

## Log snippet

From **Help → Show Log in Files**, paste lines mentioning `claudejb`,
`ClaudeSession`, `ClaudeProcess`, or the exception you saw. Wrap in a code
block:

```
<paste here>
```

## Screenshots

Optional. Drag-and-drop into the issue.

## Additional context

Anything else relevant — recent IDE update, custom MCP servers configured,
permission mode in use, etc.
