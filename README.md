# Termi-Jump

A simple terminal-based platform jumping game written in Java.

## Gameplay

The goal of Termi-Jump is to climb as high as possible by jumping on platforms.
The score is determined by the maximum height you reach. The game ends if you
fall off the bottom of the screen.

## Controls

- Move Left: A or ← (Left Arrow)
- Move Right: D or → (Right Arrow)
- Quit: Q or Esc

## Requirements

- Java 17 or newer
- Maven 3.9 or newer
- A terminal that supports interactive keyboard input

JLine handles terminal input, sizing, and ANSI support on Linux, macOS, and
Windows. The packaged JAR includes its runtime dependencies.

## How to Run

Clone the repository and navigate to the project directory:

```bash
git clone https://github.com/bahruzmammad/termi-jump.git
cd termi-jump
```

Run the game on Linux or macOS:

```bash
./run.sh
```

On Windows, run:

```bat
run.bat
```

The matching build and test commands are `./build.sh` and `./test.sh` on Linux
or macOS, and `build.bat` and `test.bat` on Windows. You can also build with
`mvn package` and launch `target/termijump.jar` directly.

## Screenshot

![Termi-Jump gameplay](imgs/gameplay.png)

![Termi-Jump gameplay with a score of 130](imgs/gameplay-score-130.png)
