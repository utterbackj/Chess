package chai;

import chesspresso.Chess;
import chesspresso.position.Position;

public class Heuristic {
	
	public static int evaluate(Position position) {
		if (position.isMate()) {
			return (position.getToPlay() == Chess.WHITE ? -1 : 1) * 200000;
		}
		if (position.isTerminal()) {
			return 0; //Draw is worse if you're winning, better if losing
		}
		return staticEval(position);
	}
	
	//Combination of Material and Positional Evaluations for each piece
	private static int staticEval(Position position) {
		int value = 0;
		int egd = 0; //determining whether this is an endgame position
		int egs = 0; //end game score, added if this is endgame
		//value += (position.getToPlay() == Chess.WHITE ? 1 : -1) * position.getMaterial();
		boolean whiteBishops = false;
		boolean blackBishops = false;
		for (int i = 0; i < 8; i++) { 
			for (int j = 0; j < 8; j++) {
				int piece = position.getPiece(Chess.coorToSqi(j, i));
				int scaling = 1;
				if (position.getColor(Chess.coorToSqi(j, i)) > 0) {
					scaling = -1;
				}
				int tempI = (scaling < 0? i : 7 - i);
				switch(piece) {
				case Chess.PAWN:
					value += 100 * scaling;
					//Add positional advantage
					value += pawnTable[tempI][j] * scaling;
					egs += pawnEndGameTable[tempI][j] * scaling;
					break;
				case Chess.BISHOP:
					egd++;
					value += 350 * scaling;
					//Reward bishop pair over just two bishops
					if (scaling > 0 && whiteBishops) {
						value += 100;
					}
					if (scaling < 0 && blackBishops) {
						value -= 100;
					}
					if (scaling > 0 && !whiteBishops) {
						whiteBishops = true;
					} 
					if (scaling < 0 && !blackBishops) {
						blackBishops = true;
					}
					//Add positional advantage
					value += bishopTable[tempI][j] * scaling;
					break;
				case Chess.KNIGHT:
					egd++;
					value += 340 * scaling;
					//Add positional advantage
					value += knightTable[tempI][j] * scaling;
					break;
				case Chess.ROOK:
					egd++;
					value += 550 * scaling;
					break;
				case Chess.QUEEN:
					egd++;
					value += 1000 * scaling;
					break;
				case Chess.KING:
					value += 200000 * scaling;
					//Add positional advantage
					value += kingTable[tempI][j] * scaling;
					egs += kingEndGameTable[tempI][j] * scaling;
					break;
				}
			}
		}
		if (egd < 6) {
			value += egs;
		}
		return value;
	}
	
	//Piece Square Tables - Concept was learned using
	//this link - https://chessprogramming.wikispaces.com/Piece-Square+tables
	
	private static int[][] pawnTable = {
			{ 0,  0,  0,  0,  0,  0,  0,  0}, //Can't happen
			{50, 50, 50, 50, 50, 50, 50, 50}, //Push forward!
			{25, 25, 35, 40, 40, 35, 25, 25}, 
			{15, 15, 20, 30, 30, 20, 15, 15}, //Center is better than outside
			{ 0,  0,  5, 20, 20,  5,  0,  0},
			{10, -5, -5,  0,  0, -5, -5, 10}, //Outside pocket isn't worse, if one square up
			{10, 10, 10,-20,-20, 10, 10, 10}, //Keep protection for king on castle
			{ 0,  0,  0,  0,  0,  0,  0,  0} //Can't happen
	};
	
	private static int[][] knightTable = {
			{-40,-30,-25,-20,-20,-25,-30,-40}, //Edges are bad
			{-30,-15, -5,  0,  0, -5,-15,-30},
			{-25, -5,  5, 10, 10,  5, -5,-25},
			{-20,  0, 10, 20, 20, 10,  0,-20}, //Center is good
			{-20,  0, 10, 20, 20, 10,  0,-20},
			{-25, -5,  5, 10, 10,  5, -5,-25},
			{-30,-15, -5,  0,  0, -5,-15,-30},
			{-40,-30,-25,-20,-20,-25,-30,-40}
	};
	
	private static int[][] bishopTable = {
			{-30,-10,-10,-10,-10,-10,-10,-30}, //Edges are bad, corners are even worse
			{-10,  0,  0,  0,  0,  0,  0,-10},
			{-10,  0,  5,  5,  5,  5,  0,-10},
			{-10,  0,  5, 10, 10,  5,  0,-10}, //Center is good, but not by that much
			{-10,  0,  5, 10, 10,  5,  0,-10},
			{-10,  5,  5,  5,  5,  5,  5,-10},
			{-10,  0,  0,  0,  0,  0,  0,-10},
			{-30,-10,-10,-10,-10,-10,-10,-30}
	};
	
	private static int[][] kingTable = { //Promotes castling
			{  0,  0,  0,  0,  0,  0,  0,  0},
			{  0,  0,  0,  0,  0,  0,  0,  0},
			{  0,  0,  0,  0,  0,  0,  0,  0},
			{  0,  0,  0,  0,  0,  0,  0,  0},
			{  0,  0,  0,  0,  0,  0,  0,  0},
			{  0,  0,  0,  0,  0,  0,  0,  0},
			{-10,-10,-10,-10,-10,-10,-10,-10}, //Stay behind protection!
			{  0,  0, 30,-10,  0,-10, 30,  0}
	};
	
	private static int[][] kingEndGameTable = { 
			{-15,-10, -5, -5, -5, -5,-10,-15},
			{-10,  0,  5, 10, 10,  5,  0,-10},
			{ -5,  5, 15, 20, 20, 15,  5, -5},
			{ -5,  5, 20, 30, 30, 20,  5, -5},
			{ -5,  5, 20, 30, 30, 20,  5, -5},
			{ -5,  5, 15, 20, 20, 15,  5, -5},
			{-10,  0,  5, 10, 10,  5,  0,-10}, 
			{-15,-10, -5, -5, -5, -5,-10,-15}
	};
	
	private static int[][] pawnEndGameTable = { //PUSH HARDER!!!
			{ 0,  0,  0,  0,  0,  0,  0,  0},
			{40, 40, 40, 40, 40, 40, 40, 40},
			{25, 25, 35, 40, 40, 35, 25, 25}, 
			{15, 15, 20, 30, 30, 20, 15, 15},
			{ 0,  0,  5, 20, 20,  5,  0,  0},
			{ 0,  0,  0,  0,  0,  0,  0,  0},
			{-10,-10,-10,-10,-10,-10,-10,-10},
			{ 0,  0,  0,  0,  0,  0,  0,  0}
	};

	public static void main(String[] args) {
		Position position = new Position(
				"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		System.out.println(evaluate(position));
	}
}
