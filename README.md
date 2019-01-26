# halite3-bot

## Introduction
This is a bot written for the [Halite 3](https://halite.io/) competition, and in fact it won it. If you didn't participate, you'll probably want to read [the rules](https://halite.io/learn-programming-challenge/game-overview) for this to make any sense at all.

Many thanks to Two Sigma and janzert for hosting such a great competition, and organising it brilliantly. Thanks also to fohristiwhirl and mlomb for creating great tools for players to analyse the game.

## Quality disclaimer
This code was written for a competition, where the incentives are very different from most other contexts. It should not be taken as an example of good code outside of a programming competition.

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

Every ship calculates the number of turns and halite burned it will take to get to every square on the map, using [Dijkstra's algorithm](https://en.wikipedia.org/wiki/Dijkstra%27s_algorithm) with a cost of number of turns. At every square, it chooses a number of turns to mine which maximise its halite gain per turn over travel and mining. The score of the square is then:

x * RETURN_TIME + (TRAVEL_TIME + MINING_TIME) * (MAX_HALITE - SHIP_HALITE)/(HALITE_MINED - HALITE_BURNED)

This score is measured in turns, and a low number is good.

This algorithm has a few properties I think are useful, and which other algorithms I tried early did not have all of:
* It is based on simple principles. This makes it more likely to work well across the map - other algorithms tended to do well locally, but go badly as the search radius expanded (although I was somewhat surprised how well it held up to the huge uncertainty in navigating across the map).
* It is pretty consistent between turns. In particular, lets say a ship is at X, selects a target T, moves towards T to X', and picks a new target T'. If nothing changes with enemies between these turns, its new target is usually (but not always) be T'. However, it will always be true that X' is closer to T' than X - so even if we've changed target, we don't regret the previous move.
* The judgement between local mining squares is very little affected by far away dropoffs. It's easy to come up with similar algorithms where the distance to a dropoff ends up affecting decisions about local mining, even when it's really just a constant cost we'll have to spend whatever we do.

I tried adding in plans about what square would be mined after the first one. These never helped - I suspect because they weren't reliable enough.

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

This value tells us whether the collision is good. In fact, it gives us a threshold h at which we want to have a collision, which is when the expression equals 0. We average these thresholds over nearby squares when planning in the future, because we expect ships to move around a bit before we get there.

These thresholds now tell us whether each ship can safely visit a particular square at a particular time. In two player games, we avoid all squares which can't be safely visited, both immediately and when planning paths for mining. In four player games, we only avoid them if we also think the opponent might visit them. For this, we track what kind of collisions each opponent is willing to risk, in order to judge whether or not they are likely to move to a spot which might cause a collision.

### Assigning targets to ships and moving
Ships claim their favourite targets (that is, the ones with the minimum score for the ship). Ships which are nearer a target get priority over those further away.

They then move towards their targets. They prefer to move along the longer axis of the path, to keep as many possible paths open as they can.

### Dropoff plan
We plan the next dropoff location, for use in mining scores. This often has the effect of ships appearing to "swarm" towards a dropoff when it gets planned.

#### Using a dropoff plan
When calculating their mining scores, ships treat the planned dropoff as real. When returning to bank their halite, they treat it as real if they expect there to be enough halite in the bank to build it when they arrive. They then build it on arrival.

#### Where and when
The dropoff logic in this bot is a bit of a mess - I never found a good way of modelling dropoffs. We consider building a dropoff on a square if a number of conditions hold:
* The number of ships is at least x * (CURRENT_DROPOFFS + 1)
* There are no other dropoffs within x of the square.
* There is at least 1 ship within x of the square, and a further y within z.

If all those are satisfied, we score the square. The score is the amount of halite nearby (with near halite counting more), plus modifiers for how well we control the area, and how far it is from the nearest dropoff. We then divide that by the cost of a dropoff minus the amount of halite on the square, to get the final score.

We model the gains from a dropoff as saving turns for ships. If there is h halite nearby, we expect to save a * h ship-turns over b turns (where a and b are constants we won't actually estimate). For this to be worth it, two things need to hold:
* It is better to build a dropoff than spawn. If we spawn 4 ships instead of building, in the same b turn span we'll get 4b ship turns. This means that a * h > 4b, or h > x for some parameter x.
* The dropoff will pay itself back. This means that a * h * SHIP_TURN_VALUE > DROPOFF_COST, or
h > x * (DROPOFF_COST / SHIP_TURN_VALUE). For this condition, we need to estimate SHIP_TURN_VALUE - we do this using imaginary ships which mine from each dropoff, using the same algorithm we use for mining for scoring.

### Returning to base
#### When to return
When ships start to return towards a dropoff or a shipyard, they carry on until they get there. They start to return when either of the following happens:
1. They get over x halite.
2. (minor) The best score they get for any square is too high - if the expected time to fill up and return is S, and they have h halite, the ship returns if:
h > x * S * DISTANCE_TO_DROPOFF

#### How to return
Each ship considers two return paths:
1. The shortest path which avoids going adjacent to any enemy.
2. The shortest path which avoids going onto any enemy.
If the path in 2. is x times shorter, it uses that path - otherwise it uses the safer path in 1.

Every turn, a ship which is returning considers instead stopping and mining. It does so if the value of the turn for a ship at its destination dropoff is less than the halite it would gather. That value is calculated by planning to mine for an imaginary ship at the dropoff.

### Spawning
In 4 player, we spawn a ship if:

y * (HALITE_LEFT_ON_MAP - x * INITIAL_MAP_HALITE) / NUM_SHIPS > COST_OF_SHIP

Here, NUM_SHIPS is for all players. The model is that we will mine down to proportion x of the initial map in total, and that will be split between NUM_SHIPS. So this is the amount the new ship will make. y accounts for there being some inspiration.

In 2 player, I mostly opted out of the spawning game. We always spawn if we have fewer ships than the opponent, and never if we have >5 more. In between, we use the 4 player spawning algorithm.

The logic is that if you are winning other parts of the game, spawning adds some variance - before implementing this system, I was winning and losing some games because of spawning decisions, and eliminating that helped my winrate overall.

### Gory details
The above describes a good bot, and I think has the most interesting and important bits, but there are a lot of small things that are in aggregate were also crucial to the bot's strength. If you want to read the code, it's hopefully pretty well commented.

## Workflow etc
Having a good development workflow is very important. I used Intellij for an IDE and Git for source control. I initially ran games on my laptop, but it is rather slow, so I ended up using (free trial) cloud computing to run games. When testing bots, I usually ran them against the previous version (in 2p) and 2 copies against 2 of the previous version (in 4p). Occasionally in 4p, I used particularly aggressive or passive versions by changing constants, when testing out collision strategies in particular.

Possibly the key skill in this game was analysing replays to understand how to improve your bot. For this, I used many strategies, including:
* Viewing replays in the exceptional [Fluorine replay viewer](https://github.com/fohristiwhirl/fluorine). I'd typically watch losses, with strategies including these:
    * Watching a whole replay for macro level problems.
    * Scrolling through collisions looking for bad ones.
    * Watching a single turtle for their whole life, to spot weirdnesses and understand how the algorithm played out.
    * I used f-logs to highlight the mining targets of turtles, making their movement much more interpretable.
* Understanding any mistakes or oddities I found - by logging locally, or by running them through [Fohristi's reloader](https://github.com/fohristiwhirl/halite3_reload) for online games.
* Looking at the breakdown of 2p, 4p, matchup sizes and opponents on [mlomb's statistics site](https://halite2018.mlomb.me). This also had very useful statistics like inspiration, dropoffs, etc.
* Collecting similar statistics for my local games, to measure the impact of changes.

I started off in Python, when I didn't expect to spend that much time on this game (I did, in fact, end up spending that much time on the game). A few weeks in, I rewrote in Java. That was definitely a good choice - I needed the performance, and even more important it made my local games faster. Also, rewriting the same algorithm from scratch made it much easier to structure it well the second time round.

I ended up with quite a lot of parameters. These were optimised in a number of ways:
* Picking a number out of the air and never changing it.
* Trying a few values locally or online.
* [CLOP](https://www.remi-coulom.fr/CLOP/). I got very mixed results for this - some sets of parameters found did very well, and some didn't. I don't have a clear pattern for what kind of parameters it was good at optimising.

## Regrets
There are a few things I would do differently next time. A couple stand out:
* I'd build my own replay viewer. Fluorine is amazing, but something I could integrate with my bot and implement whatever visualisations or metrics I would like would be great.
* For most of the competition, I kept my bot relatively principled. In my opinion, this makes it easier to develop further, and easier to understand. However, part way through the competition, I was chasing TheDuck314, who had been dominant for a while. In that period, I made some short-term decisions, which affected how easy it was to develop my bot later. The worst of these by far was dropoffs - most of my dropoffs algorithm is based on short term fixes rather than a good model of how dropoffs actually worked. I never managed to correct this.
