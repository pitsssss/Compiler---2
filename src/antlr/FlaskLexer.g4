lexer grammar FlaskLexer;

//@header {
//package antlr;
//}

tokens { INDENT, DEDENT }


//Indentation & Dedentation

@lexer::members {
    private java.util.LinkedList<Token> tokens = new java.util.LinkedList<>();
    private java.util.Stack<Integer> indents = new java.util.Stack<>();
    private int opened = 0;
    private Token lastToken = null;

    @Override
    public void emit(Token t) {
        super.setToken(t);
        tokens.offer(t);
        if (t.getChannel() == Token.DEFAULT_CHANNEL) {
            this.lastToken = t;
        }
    }

    @Override
    public Token nextToken() {
        // إذا وصلنا EOF وما زالت هناك مستويات DEDENT
        if (_input.LA(1) == EOF && !indents.isEmpty()) {

            for (int i = tokens.size() - 1; i >= 0; i--) {
                if (tokens.get(i).getType() == EOF) {
                    tokens.remove(i);
                }
            }


            while (!indents.isEmpty()) {
                this.emit(createDedent());
                indents.pop();
            }


            this.emit(commonToken(EOF, "<EOF>"));
        }

        Token next = super.nextToken();
        return tokens.isEmpty() ? next : tokens.poll();
    }

    private Token createDedent() {
        int line = (this.lastToken != null) ? this.lastToken.getLine() : 1;
        CommonToken dedent = commonToken(DEDENT, "");
        dedent.setLine(line);
        return dedent;
    }

    private CommonToken commonToken(int type, String text) {
        int stop = this.getCharIndex() - 1;
        int start = text.isEmpty() ? stop : stop - text.length() + 1;
        return new CommonToken(this._tokenFactorySourcePair, type, DEFAULT_TOKEN_CHANNEL, start, stop);
    }

    static int getIndentationCount(String spaces) {
        int count = 0;
        for (char ch : spaces.toCharArray()) {
            switch (ch) {
                case '\t':
                    count += 8 - (count % 8);
                    break;
                default:
                    count++;
            }
        }
        return count;
    }

    boolean atStartOfInput() {
        return super.getCharPositionInLine() == 0 && super.getLine() == 1;
    }
}

NEWLINE
 : ('\r'? '\n') SPACES?
   {
       String newLine = getText().replaceAll("[^\r\n]+", "");
       String spaces = getText().replaceAll("[\r\n]+", "");

       // Inside (), [] or {} a newline is insignificant (implicit line joining),
       // so it is skipped entirely and no INDENT/DEDENT is produced. This lets
       // list/dict/call literals span multiple lines.
       if (opened > 0) {
           skip();
       } else {
           emit(commonToken(NEWLINE, newLine));

           int indent = getIndentationCount(spaces);
           int previous = indents.isEmpty() ? 0 : indents.peek();

           if (indent > previous) {
               indents.push(indent);
               emit(commonToken(INDENT, spaces));
           } else if (indent < previous) {
               while(!indents.isEmpty() && indents.peek() > indent) {
                   this.emit(createDedent());
                   indents.pop();
               }

           }
       }
   }
 ;
fragment SPACES : [ \t]* ;


//Keywords
AND      : 'and';
AS       : 'as';
ASSERT   : 'assert';
BREAK    : 'break';
CLASS    : 'class';
CONTINUE : 'continue';
DEF      : 'def';
DEL      : 'del';
ELIF     : 'elif';
ELSE     : 'else';
EXCEPT   : 'except';
FALSE    : 'False';
FINALLY  : 'finally';
FOR      : 'for';
FROM     : 'from';
GLOBAL   : 'global';
IF       : 'if';
IMPORT   : 'import';
IN       : 'in';
IS       : 'is';
LAMBDA   : 'lambda';
NONE     : 'None';
NONLOCAL : 'nonlocal';
NOT      : 'not';
OR       : 'or';
PASS     : 'pass';
RAISE    : 'raise';
RETURN   : 'return';
TRUE     : 'True';
TRY      : 'try';
WHILE    : 'while';
WITH     : 'with';
YIELD    : 'yield';

// Identifiers
IDENTIFIER
    : [a-zA-Z_] [a-zA-Z_0-9]* ;

//Numbers
INT
    : '0'
    | [1-9] [0-9]*
    ;
FLOAT: [0-9]+ '.' [0-9]+ ;
SCIENTIFIC
    : INT ('.' [0-9]+)? [eE] [+-]? [0-9]+
    ;

//Strings
TRIPLE_STRING
    : '\'\'\'' .*? '\'\'\''
    | '"""' .*? '"""'
    ;
SINGLE_STRING: '\'' (~['\\] | ESC)* '\'';
DOUBLE_STRING: '"' (~["\\] | ESC)* '"' ;
fragment ESC
    :   '\\' [btnfr"'\\]    // \b \t \n \f \r \' \" \\
    |   '\\' 'x' HEX HEX    // \xhh
    |   '\\' 'u' HEX HEX HEX HEX  // \uXXXX
    ;
fragment HEX : [0-9a-fA-F] ;


//Operators
PLUS: '+';
MINUS: '-';
POW: '**';
STAR: '*';
MOD: '%';
INT_DIV: '//';
DIV: '/';

EQ: '==';
NE: '!=';
LT: '<';
LE: '<=';
GT: '>';
GE: '>=';

//Delimiters
LPAREN: '(' { opened++; };
RPAREN: ')' { opened--; };
LBRACK: '[' { opened++; };
RBRACK: ']' { opened--; };
LBRACE: '{' { opened++; };
RBRACE: '}' { opened--; };


DOT: '.' ;
COMMA: ',' ;
COLON: ':' ;
//SEMI: ';' ;

AT: '@' ;

ASSIGN: '=' ;

//ARROW: '->' ;

//ELLIPSIS: '...' ;

//Comment and Whitespace
COMMENT
    : '#' ~[\r\n]* -> skip
    ;
WS
    : [ \t]+ -> skip
    ;