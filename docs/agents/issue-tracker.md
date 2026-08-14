# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Conventions

- Create: `gh issue create --title "..." --body "..."`
- Read: `gh issue view <number> --comments`
- List: use `gh issue list` with appropriate state and label filters.
- Comment: `gh issue comment <number> --body "..."`
- Apply/remove labels: `gh issue edit <number> --add-label "..."` or `--remove-label "..."`
- Close: `gh issue close <number> --comment "..."`

Infer the repository from `git remote -v`; `gh` does this automatically inside the clone.

## Pull requests as a triage surface

**PRs as a request surface: no.**

GitHub shares one number space across issues and pull requests. If an item type is unclear, try `gh pr view <number>` and then `gh issue view <number>`.

## Skill operations

When a skill says “publish to the issue tracker,” create a GitHub issue.

When a skill says “fetch the relevant ticket,” run:

`gh issue view <number> --comments`

## Wayfinding operations

The Wayfinder map is a GitHub issue labelled `wayfinder:map`. Its decision tickets are linked as GitHub sub-issues.

- Ticket labels: `wayfinder:research`, `wayfinder:prototype`, `wayfinder:grilling`, or `wayfinder:task`.
- If GitHub sub-issues are unavailable, use a task list in the map and add `Part of #<map>` to each ticket.
- Prefer GitHub native issue dependencies for blocking relationships.
- If native dependencies are unavailable, add `Blocked by: #<number>` to the ticket body.
- An open, unblocked, unassigned child ticket is on the frontier.
- Claim a ticket with `gh issue edit <number> --add-assignee @me`.
- Resolve by commenting with the decision, closing the ticket, and adding a short linked context pointer to the map's “Decisions so far” section.
