# 🌐 Equator Graphing Calculator

Equator is a high-performance mathematical visualization engine designed for speed and precision. It leverages an advanced custom Abstract Syntax Tree (AST) compiler to evaluate functions as native Java lambdas, providing a seamless real-time graphing experience.

## ✨ Core Features

* **AST-Based Evaluation:** Compiles math strings directly to native Java lambdas for blazing-fast rendering.
* **Symbolic & Numerical Math:** Analytical differentiation powered by Symja, with a numerical central difference fallback.
* **Area & Integration:** Integral calculator utilizing Simpson's Rule with multithreaded evaluation and advanced visual gradient shading.
* **Advanced Implicit Plotting:** Multithreaded caching and dynamic-resolution Marching Squares algorithm for complex relations and inequalities.
* **Interactive LaTeX:** MathLive-powered equation input bridged natively to JavaFX.
* **Dynamic Parameters:** Auto-detects free variables and generates interactive UI sliders with sweep animation capabilities.
* **Neon UI:** Cyberpunk dark-mode with JetBrains Mono typography and dynamic Light Mode switching.

## 📐 Supported Equation Types & Syntax

Equator's parsing engine automatically detects equation types based on standard mathematical syntax.

### 1. Explicit Functions
Standard functions defined in terms of `x`.
* `y = sin(x)`
* `f(x) = x^2 - 4x + 4`
* `e^(-x^2)` *(The `y =` is automatically inferred)*

### 2. Implicit Equations
Relations involving both `x` and `y`.
* `x^2 + y^2 = 25` (Circle)
* `sin(x * y) = cos(x + y)` (Interference pattern)

### 3. Parametric Equations
Defined as coordinate pairs using the independent variable `p` or `t`.
* `(cos(p), sin(p))`
* `(t * sin(50t), t * cos(50t))`

### 4. Polar Equations
Defined using `r` and `θ` (or `t`).
* `r = 2 * (1 - cos(θ))` (Cardioid)
* `r(t) = 4 * sin(4t)` (Rose curve)

### 5. Domain Restrictions & Limits
Add curly braces to restrict the domain.
* `y = sin(x) { -pi <= x <= pi }`

### 6. Inequalities
Graph shaded regions for inequalities.
* `y < sin(x)`
* `x^2 + y^2 <= 16`

## 🏗️ Architecture & Modules

The application is structured into tightly coupled, high-performance modules:

* **`EquationParser` & `ASTCompiler`**: Parses mathematical strings (LaTeX cleaned via MathBridge) and compiles them into functional lambda interfaces (`Node.eval(x, y)`). It natively handles implicit multiplication, multi-letter functions, and domain bounds.
* **`GraphPlotter`**: The core rendering engine extending `StackPane`. It uses three layered canvases (Grid, Graph, Overlay) to optimize drawing operations. It manages coordinate transformations, zooming, panning, and background multithreading for heavy implicit graphs.
* **`MathBridge`**: Acts as the communication layer between the JavaFX `WebView` (hosting the MathLive JS library) and the Java backend. It maps LaTeX syntax to parser-friendly strings in real-time with debounce timers.
* **`derivativeCalc`**: Computes symbolic derivatives using Symja (`org.matheclipse`) and constructs numerical derivative parsers using the central difference quotient for high-fidelity rendering.
* **`integralCalc`**: Features a dedicated window with an independent graph instance. It solves roots numerically (bisection method) to find intersection bounds and calculates areas using Simpson's 1/3 rule. It also generates dynamic linear gradients to shade the calculated area.
* **`EquatorController`**: Manages the main UI, including sidebar transitions, dynamic equation cards, color pickers, parameter sliders, and real-time theme switching.

## 🛠️ Technology Stack & Dependencies

* **Language:** Java 17+
* **GUI Framework:** JavaFX (Controls, Graphics, Web)
* **Symbolic Engine:** MathEclipse / Symja (`org.matheclipse.core`)
* **Equation Input:** MathLive (HTML/JS rendered via `javafx.scene.web.WebView`)
* **Icons:** Ikonli JavaFX (`org.kordamp.ikonli.javafx.FontIcon`)

## 🚀 Getting Started

### Prerequisites
* Java JDK 17 or higher
* Maven or Gradle
* JavaFX SDK (if not managed via build tools)

### Running the Application
1. Clone the repository.
2. Ensure dependencies for `javafx-web`, `javafx-swing`, `ikonli`, and `symja` are in your `pom.xml` or `build.gradle`.
3. The main entry point is `org.example.equation_plotter.Launcher`, which pre-initializes Symja symbols on the main thread before starting the JavaFX application lifecycle.

## 📸 Key Interactivity Controls

* **Pan View:** Left-click and drag the canvas.
* **Zoom View:** Scroll wheel, or use the floating pill buttons.
* **Snap Points:** Hover over curve intersections or axis intercepts to view their coordinates (snaps via yellow/gray indicators).
* **Parameter Sweeps:** Click the "Play" icon on any parameter slider to auto-animate the curve over a customizable duration.

## 👨‍💻 Authors
Developed by **Nahid Iqbal** and **Rafid Muammar**.

---
*Built for speed. Designed for discovery.*
