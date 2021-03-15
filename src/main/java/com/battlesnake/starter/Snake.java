package com.battlesnake.starter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.*;

import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.get;

/**
 * This is a simple Battlesnake server written in Java.
 * 
 * For instructions see
 * https://github.com/BattlesnakeOfficial/starter-snake-java/README.md
 */
public class Snake {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final Handler HANDLER = new Handler();
    private static final Logger LOG = LoggerFactory.getLogger(Snake.class);

    /**
     * Main entry point.
     *
     * @param args are ignored.
     */
    public static void main(String[] args) {
        String port = System.getProperty("PORT");
        if (port != null) {
            LOG.info("Found system provided port: {}", port);
        } else {
            LOG.info("Using default port: {}", port);
            port = "8080";
        }
        port(Integer.parseInt(port));
        get("/",  HANDLER::process, JSON_MAPPER::writeValueAsString);
        post("/start", HANDLER::process, JSON_MAPPER::writeValueAsString);
        post("/move", HANDLER::process, JSON_MAPPER::writeValueAsString);
        post("/end", HANDLER::process, JSON_MAPPER::writeValueAsString);
    }

    /**
     * Handler class for dealing with the routes set up in the main method.
     */
    public static class Handler {

        /**
         * For the start/end request
         */
        private static final Map<String, String> EMPTY = new HashMap<>();

        /**
         * Generic processor that prints out the request and response from the methods.
         *
         * @param req
         * @param res
         * @return
         */
        public Map<String, String> process(Request req, Response res) {
            try {
                JsonNode parsedRequest = JSON_MAPPER.readTree(req.body());
                String uri = req.uri();
                LOG.info("{} called with: {}", uri, req.body());
                Map<String, String> snakeResponse;
                if (uri.equals("/")) {
                    snakeResponse = index();
                } else if (uri.equals("/start")) {
                    snakeResponse = start(parsedRequest);
                } else if (uri.equals("/move")) {
                    snakeResponse = move(parsedRequest);
                } else if (uri.equals("/end")) {
                    snakeResponse = end(parsedRequest);
                } else {
                    throw new IllegalAccessError("Strange call made to the snake: " + uri);
                }
                LOG.info("Responding with: {}", JSON_MAPPER.writeValueAsString(snakeResponse));
                return snakeResponse;
            } catch (Exception e) {
                LOG.warn("Something went wrong!", e);
                return null;
            }
        }

    
        /**
         * This method is called everytime your Battlesnake is entered into a game.
         * 
         * Use this method to decide how your Battlesnake is going to look on the board.
         *
         * @return a response back to the engine containing the Battlesnake setup
         *         values.
         */
        public Map<String, String> index() {         
            Map<String, String> response = new HashMap<>();
            response.put("apiversion", "1");
            response.put("author", "ChaBFic");           // TODO: Your Battlesnake Username
            response.put("color", "#888888");     // TODO: Personalize
            response.put("head", "default");  // TODO: Personalize
            response.put("tail", "default");  // TODO: Personalize
            return response;
        }

        /**
         * This method is called everytime your Battlesnake is entered into a game.
         * 
         * Use this method to decide how your Battlesnake is going to look on the board.
         *
         * @param startRequest a JSON data map containing the information about the game
         *                     that is about to be played.
         * @return responses back to the engine are ignored.
         */
        public Map<String, String> start(JsonNode startRequest) {
            LOG.info("START");
            return EMPTY;
        }

        /**
         * This method is called on every turn of a game. It's how your snake decides
         * where to move.
         * 
         * Valid moves are "up", "down", "left", or "right".
         *
         * @param moveRequest a map containing the JSON sent to this snake. Use this
         *                    data to decide your next move.
         * @return a response back to the engine containing Battlesnake movement values.
         */
        public Map<String, String> move(JsonNode moveRequest) {
            try {
                LOG.info("Data: {}", JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(moveRequest));
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            /*
                Example how to retrieve data from the request payload:

                String gameId = moveRequest.get("game").get("id").asText();
                int height = moveRequest.get("board").get("height").asInt();

            */

            String[] possibleMoves = { "up", "down", "left", "right" };

            // Choose a random direction to move in
            int choice = new Random().nextInt(possibleMoves.length);
            // String move = possibleMoves[choice];
            String nextMove = DefensiveHungry.findNextMove(moveRequest);

            LOG.info("MOVE {}", nextMove);

            Map<String, String> response = new HashMap<>();
            response.put("move", nextMove);
            return response;
        }

        /**
         * This method is called when a game your Battlesnake was in ends.
         * 
         * It is purely for informational purposes, you don't have to make any decisions
         * here.
         *
         * @param endRequest a map containing the JSON sent to this snake. Use this data
         *                   to know which game has ended
         * @return responses back to the engine are ignored.
         */
        public Map<String, String> end(JsonNode endRequest) {

            LOG.info("END");
            return EMPTY;
        }
    }

    private static class DefensiveHungry {
        public static String findNextMove(JsonNode moveRequest) {
            JsonNode board = moveRequest.get("board");
            JsonNode snake = moveRequest.get("you");
            JsonNode listOfHazards = board.get("hazards");
            int lengthOfHazards = board.get("hazards").size();
            JsonNode listOfSnakes = board.get("snakes");
            int lengthOfSnakes = board.get("snakes").size();
            HashSet<MovementService.QueueObj> obstacles = MovementService.findObstacles(listOfHazards, lengthOfHazards, listOfSnakes, lengthOfSnakes);
            String nextDirection = MovementService.getBestFood(board, snake, listOfHazards, lengthOfHazards, listOfSnakes, lengthOfSnakes, obstacles);
            return !nextDirection.isEmpty() ? nextDirection : MovementService.chaseTail(board, obstacles);
        }
    }

    private static class MovementService {

        public static String getBestFood(JsonNode board, JsonNode mySnake, JsonNode listOfHazards, int lengthOfHazards, JsonNode listOfSnakes, int lengthOfSnakes, HashSet<QueueObj> obstacles) {
            JsonNode listOfFood = board.get("food");
            int lengthOfFood = board.get("food").size();
            int bestPathLengthSoFar = Integer.MAX_VALUE;
            int mySnakeSize = mySnake.get("body").size();
            String bestDirectionSoFar = "";
            for (int i = 0; i < lengthOfFood; i++) {
                JsonNode currFood = listOfFood.get(i);
                QueueObj myShortestPathToFood;
                myShortestPathToFood = findShortestPath(mySnake.get("head"), currFood, obstacles);

                if (myShortestPathToFood == null) continue;
                if (myShortestPathToFood.distance >= bestPathLengthSoFar) continue;

                boolean willEnemyWin = false;

                for (int j = 0; j < lengthOfSnakes; j++) {
                    if (willEnemyWin) break;
                    JsonNode currEnemySnake = listOfSnakes.get(j);
                    if (currEnemySnake.get("id").equals(mySnake.get("id"))) continue;
                    QueueObj shortestEnemyPath;
                    shortestEnemyPath = findShortestPath(currEnemySnake.get("head"), currFood, obstacles);
                    if (shortestEnemyPath == null) continue;
                    if (shortestEnemyPath.distance == myShortestPathToFood.distance) {
                        willEnemyWin = mySnakeSize <= currEnemySnake.get("body").size();
                    } else {
                        willEnemyWin = myShortestPathToFood.distance > shortestEnemyPath.distance;
                    }
                }

                if(!willEnemyWin) {
                    bestPathLengthSoFar = myShortestPathToFood.distance;
                    bestDirectionSoFar = myShortestPathToFood.direction;
                }

            }
            return bestDirectionSoFar;
        }

        private static HashSet<QueueObj> findObstacles(JsonNode listOfHazards, int lengthOfHazards, JsonNode listOfSnakes, int lengthOfSnakes) {
            HashSet<QueueObj> rsf = new HashSet();
            //JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(listOfHazards);
            for (int i = 0; i  < lengthOfHazards; i++) {
                rsf.add(new QueueObj(listOfHazards.get(i).get("x").asInt(), listOfHazards.get(i).get("y").asInt()));
            }
            for (int i = 0; i  < lengthOfSnakes; i++) {
                JsonNode currSnake = listOfSnakes.get(i);
                for (int j = 0; j < currSnake.get("body").size(); j++) {
                    rsf.add(new QueueObj(currSnake.get("body").get(j).get("x").asInt(), currSnake.get("body").get(j).get("y").asInt()));
                }
            }

            return rsf;
        }



        public static QueueObj findShortestPath(JsonNode head, JsonNode target, HashSet<QueueObj> obstacles) {
            HashSet<QueueObj> seen = new HashSet<>();
            Queue<QueueObj> q = new LinkedList<>();
            int targetX = target.get("x").asInt();
            int targetY = target.get("y").asInt();
            QueueObj firstObj = new QueueObj(head.get("x").asInt(), head.get("y").asInt());
            q.offer(firstObj);
            seen.add(firstObj);
            int level = 0;
            while (!q.isEmpty()) {
                int size = q.size();
                while (size > 0) {
                    QueueObj curr = q.poll();
                    if (curr.xCoord == targetX && curr.yCoord == targetY) {
                        curr.distance = level;
                        return curr;
                    }

                    if (curr.yCoord + 1 < 11) {
                        QueueObj objToAdd = new QueueObj(curr.xCoord, curr.yCoord + 1, curr.direction);
                        if (!seen.contains(objToAdd) && !obstacles.contains(objToAdd)) {
                            if (objToAdd.direction == null) {
                                objToAdd.direction = "up";
                            }
                            q.offer(objToAdd);
                        }
                    }
                    if (curr.xCoord + 1 < 11) {
                        QueueObj objToAdd = new QueueObj(curr.xCoord + 1, curr.yCoord, curr.direction);
                        if (!seen.contains(objToAdd) && !obstacles.contains(objToAdd)) {
                            if (objToAdd.direction == null) {
                                objToAdd.direction = "right";
                            }
                            q.offer(objToAdd);
                        }
                    }
                    if (curr.yCoord - 1 >= 0) {
                        QueueObj objToAdd = new QueueObj(curr.xCoord, curr.yCoord - 1, curr.direction);
                        if (!seen.contains(objToAdd) && !obstacles.contains(objToAdd)) {
                            if (objToAdd.direction == null) {
                                objToAdd.direction = "down";
                            }
                            q.offer(objToAdd);
                        }
                    }
                    if (curr.xCoord + 1 >= 0) {
                        QueueObj objToAdd = new QueueObj(curr.xCoord - 1, curr.yCoord, curr.direction);
                        if (!seen.contains(objToAdd) && !obstacles.contains(objToAdd)) {
                            if (objToAdd.direction == null) {
                                objToAdd.direction = "left";
                            }
                            q.offer(objToAdd);
                        }
                    }
                    size--;
                }
                level++;
            }
            return null;
        }

        public static class QueueObj {
            String direction;
            int xCoord;
            int yCoord;
            int distance;

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof QueueObj)) return false;
                QueueObj queueObj = (QueueObj) o;
                return xCoord == queueObj.xCoord &&
                        yCoord == queueObj.yCoord &&
                        distance == queueObj.distance &&
                        Objects.equals(direction, queueObj.direction);
            }

            @Override
            public int hashCode() {
                return Objects.hash(direction, xCoord, yCoord, distance);
            }

            public QueueObj(int xCoord, int yCoord) {
                this.xCoord = xCoord;
                this.yCoord = yCoord;
                this.direction = null;
                this.distance = -1;
            }
            public QueueObj(int xCoord, int yCoord, String direction) {
                this.xCoord = xCoord;
                this.yCoord = yCoord;
                this.direction = direction;
                this.distance = -1;
            }
        }

        public static String chaseTail(JsonNode board, HashSet<QueueObj> obstacles) {
            JsonNode myHead = board.get("you").get("head");
            JsonNode listOfBody = board.get("you").get("body");
            int bodyLength = board.get("you").get("body").size();

            QueueObj shortestPath = findShortestPath(myHead, listOfBody.get(bodyLength - 1), obstacles);
            if (shortestPath != null) return shortestPath.direction;
            return "down"; //todo
        }
    }

}
