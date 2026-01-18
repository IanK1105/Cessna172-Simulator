# Cessna172-Simulator
This is my Java-based flight simulator inspired by the Cessna 172 to practice and expand on the coding skills I am learning in APCSA, while throuroughly integrating basic flight physics and real-world aerodynamics. My goal is to create a fun, educational simulator that models realistic flight behavior. It's a work in progress as I keep learning and adding new features.
----------
----------
## Notes
- Not a game or full flight simulator.
- An educational physics model focused on trends, not exact certification accuracy.
- Lift/drag model with stall behavior.
- Pitch dynamics (Cmα, Cm_q, elevator).
- Density altitude effects.
- Mass / wing loading variation.
- Real-time visualization (Java Swing).
- Simplified thrust model.
- No propwash on tail.
- No lateral/directional dynamics.
- Controls are shown on the simulator.
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

