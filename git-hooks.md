# Managing Git Pre-Commit Hooks

This project uses a Git pre-commit hook to run security scans (like Gitleaks) before allowing a commit. This helps prevent the accidental exposure of sensitive information. However, there are times when you might need to bypass or temporarily disable this hook, especially when committing a fix for the hook's configuration itself.

Here are the recommended ways to manage the pre-commit hook.

## Option 1: Bypass for a Single Commit (Recommended)

The safest and most direct way to bypass the pre-commit hook for a single commit is to use the `--no-verify` flag. This tells Git to skip all pre-commit and commit-message hooks for this one operation.

1.  **Stage Your Changes**:
    Make sure all your changes are staged for the commit.
    ```bash
    git add .
    ```

2.  **Commit Using `--no-verify`**:
    Run the commit command from your terminal, adding the `--no-verify` flag at the end.
    ```bash
    git commit -m "Your commit message here" --no-verify
    ```
    This will commit your changes without running the pre-commit scan.

## Option 2: Temporarily Disable the Hook Locally

If you need to disable the hook for more than one commit, you can rename the hook file. This is easily reversible and only affects your local repository.

1.  **Navigate to your project's root directory in the terminal.**

2.  **Rename the pre-commit hook file to disable it**:
    ```bash
    mv .git/hooks/pre-commit .git/hooks/pre-commit.disabled
    ```
    This effectively disables the hook. Git will no longer execute it.

3.  **Re-enable the hook when you are done**:
    To re-enable it later, simply rename it back:
    ```bash
    mv .git/hooks/pre-commit.disabled .git/hooks/pre-commit
    ```

