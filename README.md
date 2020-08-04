# RivalChess Genetic Algorithm

A Generic Algorithm for finding piece values for the Rival Chess engine.

Using the RivalChess engine, we keep the positional evaluation values, but set the piece values for pawn, knight, bishop, rook and queen
to a random number between 50 and 550.

48 sets and piece values are created (players) and are matched against each other in a league format.

A new generation of players is created based on the strength of the previous generations. Players with higher scores
will be chosen more frequently to contribute their values to the next generation.

To create a new generation, players are paired up and contribute about half of their values each to create a new player.

Random mutations can occur.

The next generation then plays in a league, and the process continues. 

## To Run

    ./gradlew run