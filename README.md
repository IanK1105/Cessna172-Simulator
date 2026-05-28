# Cessna172-Simulator
This is my Java-based flight simulator inspired by the Cessna 172 to practice and expand on my coding skills, while throuroughly integrating basic flight physics and real-world aerodynamics. My goal is to create a fun, educational simulator that models realistic flight behavior. It's a work in progress as I keep learning and adding new features. 
----------
----------
## How to run (Go to the *SimpleC172Sim.java*  file to see the code)
Ways to run the code (1 preferably):
1. Compile (copy and paste) the code into any Java IDE
2. If you have Java installed, you can install the OUTDATED version: download `SimpleFlightSim.jar` and run `java -jar SImpleFlightSim.jar`

### Notes
   
- Not a game or full flight simulator.
- An educational physics model focused on trends, not exact certification accuracy.
- Controls are shown on the simulator.

### Features

Core Aerodynamics:

- Lift/drag model with linear pre-stall behavior and post-stall degradation
- Enhanced stall modeling with gradual lift decay, increased drag, and pitching moment changes
- Stall hysteresis (different entry/recovery angles of attack)
- Stall warning system with visual indicators
- Flap effects on lift, drag, and stall characteristics

Flight Dynamics:

- Pitch dynamics with static stability (Cmα) and damping (Cm_q)
- Elevator control with trim capability
- CG position effects on stability and control authority
- First-order pitch response model for educational clarity

Environmental Effects:

- Density altitude adjustments affecting air density and performance
- Wind/gust modeling via freestream velocity modification (headwind/tailwind)

Aircraft Configuration:

- Mass/wing loading variation (900kg, 1100kg, 1400kg)
- Variable flap deployment (0-100%)
- Real-time throttle control

Interface:

- Real-time visualization (Java Swing)
- Interactive flight controls (keyboard input)
- Configurable initial conditions
- Live telemetry display (altitude, speed, AoA, pitch, etc.)

Simplified Model Assumptions:

- Quasi-steady-state aerodynamics (no unsteady effects)
- No propwash effects on tail surfaces
- No lateral/directional dynamics (2D longitudinal only)
- Point-mass thrust model

----------
## Updates
12/24/2025:

Began on the project this week (using intelliJ as my code window), setting up essential state and aircraft variables and incorporating environmental conditions and basic physics. I quickly realized that my initial plan risked poor real life accuracy, so I remodeled the code to be more extensible for future enhancements. Currently, I am struggling with tuning drag and lift to model stall behavior realistically. I've completed about 40% of the basics of flight. I aim to accurately model flight characteristics under various scenarios and environmental conditions. 

Next step: Complete the core physics module to establish foundational flight dynamics.

--------

1/3/2026:

After hitting complexity issues with advanced physics integration, I made the decision to restart with a simplified, extensible foundation. The new codebase establishes core 6DOF longitudinal flight dynamics using authentic Cessna 172 stability derivatives, and implementing proper rotational inertia. A few features I've added are:
* near realistic pitching moment modeling with tail volume coefficient
* Stall-aware damping reduction
* Density altitude effects on air density
* Configurable mass (900-1400kg) and environmental conditions
* Real-time flight instrumentation display

Problem:
The aircraft freefalls because the pitching moment equation is mis-scaled (especially the Cm_q term and tail volume usage), overdamping pitch rate and collapsing angle of attack so lift can never balance weight.
A possible solution could be to fix the moment formulation by removing extra scaling (no global damping multipliers, no double tail-volume effects), using the standard nondimensional Cm model, to allow AoA to stabilize naturally and lift to equilibrate gravity without artificial trim hacks.

--------

1/9/2026:

After fixing the freefall issue by correcting the pitching moment equation (removing overdamped Cm_q and tail volume scaling), the C172 simulator now has trimmed flight. I also replaced the mis-scaled pitch model so AoA naturally stabilizes and lift balances weight without artificial hacks.
New features added:
* Flaps: Boost lift/drag, raise stall margin (matches C172 POH behavior)
*CG: Forward CG = more stable/higher stall speed; aft CG = twitchy/lower stall
*Trim: Sets equilibrium AoA for hands-off cruise/approach
Achieved realistic behaviors: Stall curve, density altitude effects, coupled forces/moments, configurable mass—all produce believable speed/pitch/AoA responses.

DISCLAIMER: I feel there is no need to add more on to the code; however, I do not want to say it is completely finalized, and will go back and change/add things if I have to. If there are any major changes I make, I will write a memo.

Next: Systematic experiments
I want to test how flaps/CG/mass/density altitude change stall speeds, climb rates, approach behavior, and document vs real C172 data.

--------

1/17/2026:

Updated the GitHub repo:
- I’ve posted both the runnable JAR and the original Java code so anyone can access the project, and added instructions and notes in the README for how to run it and more about this project. I’ve also been using the simulator to prep for my upcoming experiments and test different flight conditions.

---------

1/23/2026:

Conducted an experiment with the simulator, and compared results with real Cessna172 data.

-----------

2/9/2026:

Implemented professor feedback from Thurow, Sharma, and Ahmed. Added wind/gust modeling via freestream modification, enhanced stall model with gradual lift degradation and hysteresis, and stall warning system with visual indicators. All features remain within quasi-steady-state framework as recommended.

-----------

