package calculator;

import java.math.BigInteger;
import java.util.*;

import static calculator.TokenType.*;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            String line = scanner.nextLine();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("/")) {
                switch (line) {
                    case "/help":
                        System.out.println("The program calculates the sum of numbers");
                        continue;
                    case "/exit":
                        System.out.println("Bye!");
                        return;
                    default:
                        System.out.println("Unknown command");
                        continue;
                }
            }

            try {
                Parser parser = new Parser(line.toCharArray());
                List<Token> tokens = parser.parseStatement();
                List<Token> convertedTokens = convertToPostfix(tokens);
                executeStatement(convertedTokens);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private static void executeStatement(List<Token> convertedTokens) throws Exception {
        Stack<Operand> operands = new Stack<>();
        for (Token token : convertedTokens) {
            switch (token.type) {
                case Number:
                    operands.push(new Operand(new BigInteger(token.value)));
                    continue;
                case Variable:
                    operands.push(new Operand(token.value));
                    continue;
                case Equal:
                    Operand value = operands.pop();
                    Operand variable = operands.pop();
                    variable.setValue(value.getValue());
                    continue;
                default:
                    BigInteger result;
                    if (token instanceof UnaryOperation) {
                        Operand operand1 = operands.pop();
                        result = ((UnaryOperation) token).apply(operand1.getValue());
                    } else if (token instanceof BinaryOperation) {
                        Operand operand2 = operands.pop();
                        Operand operand1 = operands.pop();
                        result = ((BinaryOperation) token).apply(operand1.getValue(), operand2.getValue());
                    } else {
                        throw new InvalidOperationException();
                    }

                    operands.push(new Operand(result));
            }
        }

        switch (operands.size()) {
            case 0:
                return;
            case 1:
                System.out.println(operands.pop().getValue());
                return;
            default:
                throw new InvalidOperationException();
        }
    }

    private static List<Token> convertToPostfix(List<Token> tokens) throws Exception {
        Stack<Token> operators = new Stack<>();
        List<Token> result = new ArrayList<>();

        for (Token token : tokens) {
            if (token.type == Number || token.type == Variable) {
                result.add(token);
                continue;
            }

            if (operators.empty() || operators.peek().type == LeftCurl || token.type == LeftCurl) {
                operators.push(token);
                continue;
            }

            if (token.type == RightCurl) {
                Token operand = operators.pop();
                while (operand.type != LeftCurl) {
                    result.add(operand);
                    operand = operators.pop();
                }
                continue;
            }

            Token top = operators.peek();
            if (!(top instanceof Operation) || !(token instanceof Operation)) {
                throw new InvalidOperationException();
            }

            while (top != null && top.type != LeftCurl &&
                    ((Operation) top).getPrecedence() >= ((Operation) token).getPrecedence()) {
                result.add(operators.pop());
                if (operators.empty()) {
                    top = null;
                } else {
                    top = operators.peek();
                }
            }
            operators.push(token);
        }

        while (!operators.empty()) {
            result.add(operators.pop());
        }

        return result;
    }
}

class Operand {
    private static final Map<String, BigInteger> variables = new HashMap<>();
    private String variableName;
    private BigInteger value;

    public Operand(String variableName) {
        this.variableName = variableName;
    }

    public Operand(BigInteger value) {
        this.value = value;
    }

    public BigInteger getValue() throws UnknownVariableException {
        if (variableName != null) {
            if (variables.containsKey(variableName)) {
                value = variables.get(variableName);
            } else {
                throw new UnknownVariableException();
            }
        }

        return value;
    }

    public void setValue(BigInteger value) {
        this.value = value;
        if (variableName != null) {
            variables.put(variableName, value);
        }
    }

    @Override
    public String toString() {
        return variableName != null ? variableName : String.valueOf(value);
    }
}

class UnknownVariableException extends Exception {
    public UnknownVariableException() {
        super("Unknown variable");
    }
}

class InvalidIdentifierException extends Exception {
    public InvalidIdentifierException() {
        super("Invalid identifier");
    }
}

class InvalidNumberException extends Exception {
    public InvalidNumberException() {
        super("Invalid number");
    }
}

class InvalidOperationException extends Exception {
    public InvalidOperationException() {
        super("Invalid expression");
    }
}

class InvalidAssignmentException extends Exception {
    public InvalidAssignmentException() {
        super("Invalid assignment");
    }
}

class Parser {
    private int pos = 0;
    private final char[] source;
    private final List<Token> tokens = new ArrayList<>();
    private Token token;

    public Parser(char[] source) {
        this.source = source;
    }

    public List<Token> parseStatement() throws Exception {
        token = getNextToken();
        if (token.type == EOL) {
            return tokens;
        }
        if (token.type == Variable) {
            tokens.add(token);
            token = getNextToken();
            if (token.type == EOL) {
                return tokens;
            }
            if (!(token instanceof Operation) || !token.type.isBinaryOperation()) {
                throw new InvalidOperationException();
            }

            parseOperation();
        }
        parseExpression();
        if (token.type != EOL) {
            throw new InvalidOperationException();
        }
        return tokens;
    }

    private void parseExpression() throws Exception {
        parseOperand();

        while (token instanceof Operation && token.type.isBinaryOperation()) {
            if (token.type == Equal) {
                throw new InvalidAssignmentException();
            }
            parseOperation();

            parseOperand();
        }
    }

    private void parseOperation() throws Exception {
        Operation operation = (Operation) token;
        token = getNextToken(false);
        boolean odd = true;
        while ((token.type == Plus || token.type == Minus) &&
                token.type == operation.type) {
            token = getNextToken(false);
            odd = !odd;
        }
        if (!odd) {
            operation = new Operation(Plus, '+');
        }

        tokens.add(operation.toBinary());
        if (token.type == Space) {
            token = getNextToken();
        }
    }

    private void parseOperand() throws Exception {
        if (token.type == LeftCurl) {
            tokens.add(token);
            token = getNextToken();
            parseExpression();
            if (token.type != RightCurl) {
                throw new InvalidOperationException();
            }
            tokens.add(token);
            token = getNextToken();
            return;
        }

        if (token instanceof Operation && token.type.isUnaryOperation()) {
            tokens.add(((Operation) token).toUnary());
            token = getNextToken();
        }

        if (token.type != Number && token.type != Variable) {
            throw new InvalidOperationException();
        }
        tokens.add(token);
        token = getNextToken();
    }

    private Token getNextToken() throws Exception {
        return getNextToken(true);
    }

    private Token getNextToken(boolean skipWhitespace) throws Exception {
        if (skipWhitespace) {
            while (pos < source.length &&
                    source[pos] == ' ') {
                pos++;
            }
        }
        if (pos == source.length) {
            return new Token(EOL, "");
        }

        Token nextToken = null;
        switch (source[pos]) {
            case '+':
                nextToken = new Operation(Plus, source[pos]);
                break;
            case '-':
                nextToken = new Operation(Minus, source[pos]);
                break;
            case '*':
                nextToken = new Operation(Mul, source[pos]);
                break;
            case '/':
                nextToken = new Operation(Div, source[pos]);
                break;
            case '^':
                nextToken = new Operation(Power, source[pos]);
                break;
            case '=':
                nextToken = new Operation(Equal, source[pos]);
                break;
            case '(':
                nextToken = new Token(LeftCurl, source[pos]);
                break;
            case ')':
                nextToken = new Token(RightCurl, source[pos]);
                break;
            case ' ':
                nextToken = new Token(Space, source[pos]);
                break;
        }

        if (nextToken != null) {
            pos++;
            return nextToken;
        }

        int oldPos = pos;
//        Number
        while (pos < source.length &&
                isDigit(source[pos])) {
            pos++;
        }
        if (pos != oldPos) {
            if (pos < source.length && isAlpha(source[pos])) {
                throw new InvalidNumberException();
            }
            return new Token(Number, String.valueOf(source, oldPos, pos - oldPos));
        }

//        Variable
        while (pos < source.length &&
                isAlpha(source[pos])) {
            pos++;
        }
        if (pos != oldPos) {
            if (pos < source.length && isDigit(source[pos])) {
                throw new InvalidIdentifierException();
            }
            return new Token(Variable, String.valueOf(source, oldPos, pos - oldPos));
        }

        throw new InvalidOperationException();
    }

    static boolean isDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    static boolean isAlpha(char ch) {
        return ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z';
    }
}

class Token {
    TokenType type;
    String value;

    public Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
    }

    public Token(TokenType type, char value) {
        this.type = type;
        this.value = String.valueOf(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

class Operation extends Token {
    public Operation(TokenType type, String value) {
        super(type, value);
    }

    public Operation(TokenType type, char value) {
        super(type, value);
    }

    public Operation toUnary() {
        return new UnaryOperation(type, value);
    }

    public Operation toBinary() {
        return new BinaryOperation(type, value);
    }

    public int getPrecedence() {
        return type.getPrecedence();
    }
}

class UnaryOperation extends Operation {
    public UnaryOperation(TokenType type, String value) {
        super(type, value);
    }

    public BigInteger apply(BigInteger operand1) throws Exception {
        switch (type) {
            case Plus:
                return operand1;
            case Minus:
                return operand1.negate();
            default:
                throw new InvalidOperationException();
        }
    }

    @Override
    public int getPrecedence() {
        return 9;
    }
}

class BinaryOperation extends Operation {
    public BinaryOperation(TokenType type, String value) {
        super(type, value);
    }

    public BigInteger apply(BigInteger operand1, BigInteger operand2) throws Exception {
        switch (type) {
            case Plus:
                return operand1.add(operand2);
            case Minus:
                return operand1.subtract(operand2);
            case Mul:
                return operand1.multiply(operand2);
            case Div:
                return operand1.divide(operand2);
            case Power:
                return operand1.pow(operand2.intValue());
            default:
                throw new InvalidOperationException();
        }
    }
}

enum TokenType {
    Equal(4),
    Plus(5),
    Minus(5),
    Mul(6),
    Div(6),
    Power(7),
    LeftCurl(8),
    RightCurl(0),
    Number(10),
    Variable(10),
    Space(0),
    EOL(0);

    private final int precedence;

    TokenType(int precedence) {
        this.precedence = precedence;
    }

    public int getPrecedence() {
        return precedence;
    }

    public boolean isUnaryOperation() {
        return this == Plus || this == Minus;
    }

    public boolean isBinaryOperation() {
        return List.of(Plus, Minus, Mul, Div, Power, Equal).contains(this);
    }
}
