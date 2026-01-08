# Create and push repository to GitHub using gh CLI
# Usage: Open PowerShell in repo root and run: .\scripts\create_and_push_repo.ps1

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    Write-Host "Repository create-and-push helper starting..."

    # Determine repo name from current folder
    $repoName = Split-Path -Leaf (Get-Location)
    Write-Host "Detected repo name: $repoName"

    # Ensure git is available
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Write-Error "git is not installed or not in PATH. Install Git and retry."; exit 1
    }

    # Initialize git repo if needed (use exit codes, suppress git error text)
    $isRepo = $false
    try {
        $null = git rev-parse --is-inside-work-tree 2>$null
        if ($LASTEXITCODE -eq 0) { $isRepo = $true }
    } catch {
        $isRepo = $false
    }

    if (-not $isRepo) {
        Write-Host "No git repository found. Initializing a new git repository..."
        git init | Out-Null
    } else {
        Write-Host "Git repository detected."
    }

    # Ensure there is at least one commit (create an initial empty commit if necessary)
    $hasHead = $false
    try { git rev-parse --verify HEAD 2>$null | Out-Null; if ($LASTEXITCODE -eq 0) { $hasHead = $true } } catch { $hasHead = $false }
    if (-not $hasHead) {
        Write-Host "No commits found. Creating an initial commit..."
        git add -A
        # create an empty commit if nothing staged to ensure repo has HEAD
        git commit -m "chore: initial commit" --allow-empty | Out-Null
    }

    # Ensure gh is installed
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        Write-Error "GitHub CLI (gh) is not installed or not in PATH. Please install it from https://cli.github.com/ and authenticate (gh auth login)."; exit 1
    }

    # Check authentication
    $authOK = $false
    try {
        gh auth status -h github.com 2>$null | Out-Null; $authOK = $true
    } catch {
        Write-Host "gh CLI not authenticated. Attempting interactive login..."
        gh auth login --web
        try { gh auth status -h github.com 2>$null | Out-Null; $authOK = $true } catch { $authOK = $false }
    }

    if (-not $authOK) { Write-Error "gh authentication failed. Please run 'gh auth login' and try again."; exit 1 }

    # If origin exists, push to it; otherwise create repo and push
    $originUrl = $null
    try { $originUrl = git config --get remote.origin.url 2>$null } catch { $originUrl = $null }

    if ($originUrl) {
        Write-Host "Remote 'origin' already set to: $originUrl. Pushing to existing remote..."
        $branch = git rev-parse --abbrev-ref HEAD
        if (-not $branch -or $branch -eq 'HEAD') { git branch -M main; $branch = 'main' }
        git push -u origin $branch
        Write-Host "Pushed to existing origin. Done."
        exit 0
    }

    # Create repo using gh and push source
    Write-Host "Creating repository '$repoName' on GitHub and pushing..."
    gh repo create $repoName --public --source . --remote origin --push --confirm

    $ownerRepo = (gh repo view --json nameWithOwner -q .nameWithOwner)
    if ($ownerRepo) {
        Write-Host "Repository created and pushed: https://github.com/$ownerRepo"
    } else {
        Write-Host "Repository created and pushed."
    }

} catch {
    Write-Error "Error: $_"
    exit 1
}
