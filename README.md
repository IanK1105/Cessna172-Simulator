# Cessna172-Simulator
This is my Java-based flight simulator inspired by the Cessna 172 to practice and expand on my coding skills, while throuroughly integrating basic flight physics and real-world aerodynamics. My goal is to create an educational simulator that models realistic flight behavior. It's a work in progress as I keep learning and adding new features. 
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
