# Skopio for JetBrains

Skopio is a lightweight JetBrains IDE plugin that tracks how you spend time inside your editor and records structured activity events for deeper productivity insights.

The plugin runs quietly in the background, detects what you’re working on, categorizes your activity, and sends events to the Skopio CLI, which stores them locally and syncs them to the Skopio desktop app for analysis.

## What Skopio Tracks

Skopio records **time-based activity events**, not keystrokes or code content.

Each event includes:

- **Start and end time**
- **Duration (seconds)**
- **Active file or app**
- **Project**
- **Activity category**

### Supported Activity Categories

Skopio automatically classifies your work into:
- **Coding** – editing and navigating source files 
- **Debugging** – running or debugging configurations 
- **Compiling** – builds and compilation activity 
- **Testing** – test runs 
- **Writing Docs** – documentation-focused work 
- **Code Reviewing** – diffs, VCS views, and review contexts

## How it works

1.	Skopio listens to IDE events such as:
      - file selection changes
      - run/debug lifecycle
      - build lifecycle
      - diff editors and VCS tool windows
2.	Activity is coalesced intelligently:
      - short gaps are merged
      - rapid file switches don’t create noise
      - events are finalized only when activity truly changes
3.	Events are buffered locally and periodically sent to the Skopio CLI, which:
      - stores them in a local database
      - syncs them to the Skopio desktop app

## Privacy-First by Design

Skopio is built with privacy in mind.

- No keystrokes are recorded
- No file contents are read
- Only metadata (time, file path, category) is captured
- All data is stored locally and encrypted

## Platform Support

- **macOS only (for now)**
- Compatible with **all JetBrains IDEs** (IntelliJ IDEA, WebStorm, PyCharm, GoLand, CLion, etc.)

## Requirements

- A JetBrains IDE (2025.x or later)

## :rocket: Getting Started

1. Install the Skopio plugin from the JetBrains Marketplace
2. Open any project
3. Start working — Skopio runs automatically
4. View insights in the Skopio desktop app

## License
[MIT License](./LICENSE)
