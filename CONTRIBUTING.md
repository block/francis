# Building and running
You can run `scripts/francis` to build and run. Arguments passed will be forwarded onto francis.

# Tests
Run `scripts/test.sh`. You will need a device/emulator attached.

# Git Hooks
This repo provides a repo-local hook at `.hooks/pre-push`. In environments with shared hook dispatch configured, it checks only the Kotlin files being pushed with `ktfmt`.

Formatting is also enforced in CI because `build` runs `check`, and `check` depends on `ktfmtCheck`.

# Debugging
Consult the log files. Even if you don't request debug logs, they will be logged.
Look in `$XDG_STATE_HOME/francis/runs` (defaults to
`~/.local/state/francis/runs`) for logs from past runs.
