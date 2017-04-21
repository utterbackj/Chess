//By Joshua Utterback
package chai;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.move.IllegalMoveException;
import chesspresso.pgn.PGNReader;
import chesspresso.position.Position;

public class ModestAI implements ChessAI {
	
	private static int MAX_TABLE_ENTRIES = 500000;
	
	private short bestMove;
	private int bestScore;
	private int scaling;
	private LinkedHashMap<Position, Result> posTable = createTable();
	private ArrayList<Game> openingBook = new ArrayList<>();
	private boolean usingOpening = true;
	
	public ModestAI() {
		addBooks();
	}
	
	//Discovered this memory clearing function with
	//http://stackoverflow.com/questions/11469045/how-to-limit-the-maximum-size-of-a-map-by-
	//removing-oldest-entries-when-limit-rea
	private LinkedHashMap<Position, Result> createTable() {
		return new LinkedHashMap<Position, Result>((MAX_TABLE_ENTRIES) * 10/7, 0.7f, true) {
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<Position, Result> eldest) {
				return size() > MAX_TABLE_ENTRIES;
			}
		};
	}

	@Override
	public short getMove(Position position) {
		long time = System.nanoTime();
		if (usingOpening) {
			short result = parseBooks(position);
			if (result != -1) {
				return result;
			}
			usingOpening = false;
		}
		for (short i = 0; i < 6; i++) {
			bestScore = 0;
			short tempMove = alphaBeta(position, i);
			System.out.println("Time elapsed for depth " + i + ": " + (System.nanoTime() - time)/1000000);
			if (bestScore >= 200000) {
				bestMove = tempMove;
				break; //If a checkmate can be forced, force it.
			}
			if (bestScore <= -150000 && i != 0) {
				return bestMove; //If the game is "lost", hope opponent has less depth
			}
			bestMove = tempMove;
			System.out.println("Memory usage of depth " + i + ": " + posTable.size());
		}
		return bestMove;
	}
	
	private void addBooks() {
		String url = "provided_chess/book.pgn";
		try {
			File file = new File(url);
			FileInputStream inputStream = new FileInputStream(file);
			PGNReader pgnReader = new PGNReader(inputStream, "book.pgn");

			for (int i = 0; i < 120; i++)  {
				Game game = pgnReader.parseGame();
				openingBook.add(game);
			}
		} catch (Exception e) {
			System.err.println("Unable to parse opening book pgn.");
			e.printStackTrace();
		}
	}
	
	private short parseBooks(Position position) {
		ArrayList<Game> possibleGames = new ArrayList<Game>();
		for (Game game : openingBook) {
			if (game.containsPosition(position)) {
				possibleGames.add(game);
			}
		}
		Game wonGame = null;
		Game drawGame = null;
		//Of all games that we could be in, winning is better
		for (Game game : possibleGames) {
			if ((position.getToPlay() == Chess.WHITE && game.getResult() == Chess.RES_WHITE_WINS) ||
					position.getToPlay() == Chess.BLACK && game.getResult() == Chess.RES_BLACK_WINS) {
				wonGame = game;
				break;
			} else if (game.getResult() == Chess.RES_DRAW) {
				drawGame = game;
			} 
		}
		//Figure out what game we chose and make the move from it
		Game chosenGame = wonGame != null ? wonGame : drawGame;
		if (chosenGame != null) {
			chosenGame.gotoPosition(position);
			int distance = chosenGame.getNumOfMoves() - chosenGame.getCurrentMoveNumber();
			//If finished moving, stop
			if (distance <= 1) {
				return -1;
			} else {
				return chosenGame.getNextShortMove();
			}
		}
		return -1;
	}
	
	private boolean cutOff(Position position, short depth) {
		if (depth <= 0) {
			return true;
		}
		if (position.isTerminal()) {
			return true;
		}
		return false;
	}
	
	
	//Move ordering suggestions found at https://chessprogramming.wikispaces.com/Move+Ordering
	//although none of the full techniques listed are implemented here
	private short alphaBeta(Position position, short maxDepth) {
		short[] captures = position.getAllCapturingMoves();
		short[] nonCaptures = position.getAllNonCapturingMoves();
		short[] actions = new short[captures.length + nonCaptures.length];
		System.arraycopy(captures, 0, actions, 0, captures.length);
		System.arraycopy(nonCaptures, 0, actions, captures.length, nonCaptures.length);
		short bestAction = actions[0];
		int a = Integer.MIN_VALUE;
		int b = Integer.MAX_VALUE;
		scaling = (position.getToPlay() == Chess.WHITE? 1 : -1);
		//Try to put best move first
		if (posTable.containsKey(position) && posTable.get(position).bestMove != -1) { 
			short guessBestMove = posTable.get(position).bestMove;
			for (int i = 0; i < actions.length; i++) {
				if (actions[i] == guessBestMove) {
					actions[i] = actions[0];
					actions[0] = guessBestMove;
					break;
				}
			}
		}
		//Iterate through moves
		for (int i = 0; i < actions.length; i++) {
			try {
				Position newPos = new Position(position);
				newPos.doMove(actions[i]);
				int value = abMin(newPos, maxDepth, a, b);
				if (value > a) {
					a = value;
					bestAction = actions[i];
				}
				//position.undoMove();
			} catch (IllegalMoveException e) {
				System.err.println("Illegal move attempted, this shouldn't be possible.");
				e.printStackTrace();
				continue;
			}
		}
		bestScore = a;
		System.out.println("Best score: " + a);
		System.out.println("Best action: " + bestAction);
		return bestAction;
	}
	
	private int abMax(Position position, short depth, int a, int b) {
		if (cutOff(position, depth)) {
			if (!posTable.containsKey(position)) {
				posTable.put(position, new Result(scaling * Heuristic.evaluate(position), depth, (short)-1));
			} 
			return posTable.get(position).score;
		}
		if (posTable.containsKey(position) && posTable.get(position).depth >= depth) {
			return posTable.get(position).score;
		}
		short[] captures = position.getAllCapturingMoves();
		short[] nonCaptures = position.getAllNonCapturingMoves();
		short[] actions = new short[captures.length + nonCaptures.length];
		System.arraycopy(captures, 0, actions, 0, captures.length);
		System.arraycopy(nonCaptures, 0, actions, captures.length, nonCaptures.length);
		//Try to put best move first
		if (posTable.containsKey(position) && posTable.get(position).bestMove != -1) { 
			short guessBestMove = posTable.get(position).bestMove;
			for (int i = 0; i < actions.length; i++) {
				if (actions[i] == guessBestMove) {
					actions[i] = actions[0];
					actions[0] = guessBestMove;
					break;
				}
			}
		}
		short bestMove = actions[0];
		//Iterate through all moves
		for (int i = 0; i < actions.length; i++) {
			try {
				Position newPos = new Position(position);
				newPos.doMove(actions[i]);
				//position.doMove(actions[i]);
				int result = abMin(newPos, (short) (depth - 1), a, b);
				if (result >= b) {
					return b;
				}
				if (result > a) {
					a = result;
					bestMove = actions[i];
				}
				//position.undoMove();
			} catch (IllegalMoveException e) {
				System.err.println("Illegal move attempted, this shouldn't be possible.");
				e.printStackTrace();
				System.exit(1);
			}
		}
		posTable.put(position, new Result(a, depth, bestMove));
		return a;
	}
	
	private int abMin(Position position, short depth, int a, int b) {
		if (cutOff(position, depth)) {
			//return scaling * Heuristic.evaluate(position);
			if (!posTable.containsKey(position)) {
				posTable.put(position, new Result(scaling * Heuristic.evaluate(position), depth, (short)-1));
			}
			return posTable.get(position).score;
		}
		if (posTable.containsKey(position) && posTable.get(position).depth >= depth) {
			return posTable.get(position).score;
		}
		short[] captures = position.getAllCapturingMoves();
		short[] nonCaptures = position.getAllNonCapturingMoves();
		short[] actions = new short[captures.length + nonCaptures.length];
		System.arraycopy(captures, 0, actions, 0, captures.length);
		System.arraycopy(nonCaptures, 0, actions, captures.length, nonCaptures.length);
		short bestMove = actions[0];
		//Try to put best move first
		if (posTable.containsKey(position) && posTable.get(position).bestMove != -1) { 
			short guessBestMove = posTable.get(position).bestMove;
			for (int i = 0; i < actions.length; i++) {
				if (actions[i] == guessBestMove) {
					actions[i] = actions[0];
					actions[0] = guessBestMove;
					break;
				}
			}
		}
		//Iterate through all moves
		for (int i = 0; i < actions.length; i++) {
			try {
				Position newPos = new Position(position);
				newPos.doMove(actions[i]);
				//position.doMove(actions[i]);
				int result = abMax(newPos, (short) (depth - 1), a, b);
				if (result <= a) {
					return a;
				}
				if (result < b) {
					b = result;
					bestMove = actions[i];
				}
				//position.undoMove();
			} catch (IllegalMoveException e) {
				System.err.println("Illegal move attempted, this shouldn't be possible.");
				e.printStackTrace();
				System.exit(1);
			}
		}
		posTable.put(position, new Result(b, depth, bestMove));
		return b;
	}
	
	/*private short minimax(Position position, short maxDepth) {
		short[] actions = position.getAllMoves();
		short bestAction = actions[0];
		int bestValue = Integer.MIN_VALUE;
		scaling = (position.getToPlay() == Chess.WHITE? 1 : -1);
		for (int i = 0; i < actions.length; i++) {
			try {
				Position newPos = new Position(position);
				newPos.doMove(actions[i]);
				int value = minValue(newPos, maxDepth);
				if (value > bestValue) {
					bestValue = value;
					bestAction = actions[i];
				}
				//position.undoMove();
			} catch (IllegalMoveException e) {
				System.err.println("Illegal move attempted, this shouldn't be possible.");
				e.printStackTrace();
				continue;
			}
		}
		System.out.println("Best score: " + bestValue);
		return bestAction;
	}*/
	
	private int maxValue(Position position, short depth) {
		if (cutOff(position, depth)) {
			return scaling * Heuristic.evaluate(position);
		}
		int value = Integer.MIN_VALUE;
		short[] actions = position.getAllMoves();
		for (int i = 0; i < actions.length; i++) {
			try {
				position.doMove(actions[i]);
				int result = minValue(position, (short) (depth - 1));
				if (result > value) {
					value = result;
				}
				position.undoMove();
			} catch (IllegalMoveException e) {
				System.err.println("Illegal move attempted, this shouldn't be possible.");
				e.printStackTrace();
				System.exit(1);
			}
		}
		return value;
	}
	
	private int minValue(Position position, short depth) {
		if (cutOff(position, depth)) {
			return scaling * Heuristic.evaluate(position);
		}
		int value = Integer.MAX_VALUE;
		short[] actions = position.getAllMoves();
		for (int i = 0; i < actions.length; i++) {
			try {
				position.doMove(actions[i]);
				int result = maxValue(position, (short) (depth - 1));
				if (result < value) {
					value = result;
				}
				position.undoMove();
			} catch (IllegalMoveException e) {
				System.err.println("Illegal move attempted, this shouldn't be possible.");
				e.printStackTrace();
				System.exit(1);
			}
		}
		return value;
	}
	
	private class Result {
		public int score;
		public short depth;
		public short bestMove;
		
		public Result(int score, short depth, short bestMove) {
			this.score = score;
			this.depth = depth;
			this.bestMove = bestMove;
		}
	}
}
