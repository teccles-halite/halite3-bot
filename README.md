# halite3-bot
A bot for Halite 3.

This README is a work in progress.

## Algorithm
This will not be a complete description of the algorithm, but will have a summary of the important things (insofar as I know what they are). "x" and "y" are used to mean "a parameter", which may be different between map sizes and number of players in the game. I'll indicate some features which are peripheral and don't make much impact on the overall strength as (minor).

### Summary of a turn
1. Build dropoffs on exceptional squares (minor).
2. Calculate moves for ships which cannot move.
4. Plan our next dropoff site (if any)
5. For every non-returning ship, calculate a score for every square.
6. Work out which ships are returning towards dropoffs this turn.
7. Calculate moves for ships returning towards dropoffs.
8. Decide whether to spawn a ship.
9. A few small features - opportunistic hunting, guarding our dropoffs in the endgame, moving multiple turtles towards piles we are fighting over (minor).
10. Assign all other ships mining targets according to their scores, and move each towards its target.

### Scoring squares
#### Basic algorithm
The model used for this algorithm is: a ship will move to a square X, and mine it for a while. It will then continue to move-and-mine with the same gathering efficiency until full. It will then return to a dropoff, from a point exactly as far away as square X. It should pick the square X to minimise the number of turns this whole process takes.

Every ship calculates the number of turns and halite burned it will take to get to every square on the map, using a breadth first search. At every square, it chooses a number of turns to mine which maximise its halite gain per turn over travel and mining. The score of the square is then:

x * RETURN_TIME + (TRAVEL_TIME + MINING_TIME) * (MAX_HALITE - SHIP_HALITE)/(HALITE_MINED - HALITE_BURNED)

This score is measured in turns, and a low number is good.

#### Inspiration
Every square is assigned some inspiration value, which modifies how much we mine on it (just like real inspiration). The main way of doing this is for squares which are actually inspired right now. These get an inspiration bonus of 2 (the true inspiration bonus) on turn 1 of the plan, falling linearly over x turns to some minimum value y.

Additionally, a small bonus is given to squares which have at least one enemy quite near them.

#### Nearby halite (minor)
We give squares a small bonus for having lots of halite nearby.

#### Collision avoidance
First, we define a desirable collision. A player's ownership of a square is how many ships they have near it compared to other players, with nearby ships counting more. Suppose that we have a possible collision, with:
* Our ships having p control of the square.
* Our colliding ship having h halite.
* The enemy ship having e halite.
* The value of having a ship on the board being s (this is calculated in the spawning algorithm)
* There are n players in the game

Then the value of the collision to us is:

OUR_VALUE_AFTER - OUR_VALUE_BEFORE - (THEIR_VALUE_AFTER - THEIR_VALUE_BEFORE)/(NUM_PLAYERS-1) =
p(h+e) - h - s - ((1-p)(h+e) - e - s)/(n-1)

This equation tells us whether the collision is good. In fact, it gives us a threshold h at which we want to have a collision. We average these thresholds over nearby squares when planning in the future, because we expect ships to move around a bit before we get there.

These thresholds now tell us whether each ship can safely visit a particular square at a particular time. In two player games, we avoid all squares which can't be safely visited, both immediately and when planning paths for mining. In four player games, we only avoid them if we also think the opponent might visit them. For this, we track what kind of collisions each opponent is willing to risk, in order to judge whether or not they are likely to move to a spot which might cause a collision.

### Assigning targets to ships
Ships claim their favourite targets (that is, the ones with the minimum score for the ship). Ships which are nearer a target get priority over those further away.

They then move towards their targets. They prefer to move along the longer axis of the path, to keep as many possible paths open as they can.

### Dropoff plan
#### Using a dropoff plan
#### Where and when

### Returning to base
#### When to return
#### How to return

### Spawning


