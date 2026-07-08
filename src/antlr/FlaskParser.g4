parser grammar FlaskParser;

//@header {
//package antlr;
//}

options {
    tokenVocab = FlaskLexer;
}

// ======================================================
// PROGRAM
// ======================================================

prog
    : stmt* EOF
    ;

// ======================================================
// STATEMENTS
// ======================================================

stmt
    : simple_stmt NEWLINE              # SimpleStmtLine
    | compound_stmt                    # CompoundStmt
    | NEWLINE                          # EmptyLine
    ;

// ======================================================
// SIMPLE STATEMENTS
// ======================================================

simple_stmt
    : assignment
    | return_stmt
    | pass_stmt
    | break_stmt
    | continue_stmt
    | del_stmt
    | global_stmt
    | import_stmt
    | expr
    ;


assignment
    : assign_target (ASSIGN assign_target)* ASSIGN expr
    ;

// LHS target: identifier + (.name | [expr])*
assign_target
    : IDENTIFIER ((DOT IDENTIFIER) | (LBRACK expr RBRACK))*
    ;

return_stmt
    : RETURN expr
    ;

pass_stmt
    : PASS
    ;

break_stmt
    : BREAK
    ;

continue_stmt
    : CONTINUE
    ;

del_stmt
    : DEL expr (COMMA expr)*
    ;

global_stmt
    : GLOBAL IDENTIFIER (COMMA IDENTIFIER)*
    ;

import_stmt
    : IMPORT dotted_name (COMMA dotted_name)*                # ImportModule
    | FROM dotted_name IMPORT IDENTIFIER (COMMA IDENTIFIER)* # FromImport
    ;

// ======================================================
// COMPOUND STATEMENTS
// ======================================================

compound_stmt
    : if_stmt                          # IfStmt
    | while_stmt                       # WhileStmt
    | for_stmt                         # ForStmt
    | func_def                         # FuncDefStmt
    | class_def                        # ClassDefStmt
    | decorated                        # DecoratedStmt
    ;



if_stmt
    : IF expr COLON suite
      (ELIF expr COLON suite)*
      (ELSE COLON suite)?
    ;



while_stmt
    : WHILE expr COLON suite
    ;



for_stmt
    : FOR IDENTIFIER IN expr COLON suite
    ;



func_def
    : DEF IDENTIFIER LPAREN parameters? RPAREN COLON suite
    ;

parameters
    : IDENTIFIER (COMMA IDENTIFIER)*
    ;



class_def
    : CLASS IDENTIFIER (LPAREN IDENTIFIER RPAREN)? COLON suite
    ;



decorator
    : AT primary NEWLINE
    ;

decorated
    : decorator+ (func_def | class_def)
    ;



suite
    : simple_stmt NEWLINE                       # InlineSuite
    | NEWLINE INDENT stmt+ DEDENT               # BlockSuite
    ;

// ======================================================
// EXPRESSIONS (layered precedence)
// ======================================================

// expr = entry point
expr
    : lambda_expr                               # ExprRoot
    ;

// lambda has the lowest precedence in this simplified model
lambda_expr
    : LAMBDA lambda_params? COLON expr          # LambdaExpr
    | or_expr                                   # ToOrExpr
    ;

// OR
or_expr
    : and_expr (OR and_expr)*                   # OrExpr
    ;

// AND
and_expr
    : not_expr (AND not_expr)*                  # AndExpr
    ;

// NOT (unary logical)
not_expr
    : NOT not_expr                              # NotExpr
    | comparison                                # ToCompareExpr
    ;

// comparisons (including IS / IS NOT)
comparison
    : arith_expr (comp_tail)*                   # CompareExpr
    ;

comp_tail
    : (LT | GT | LE | GE | EQ | NE) arith_expr   # RelOp
    | IS (NOT)? arith_expr                       # IsOp
    ;

// +, -
arith_expr
    : term ((PLUS | MINUS) term)*               # AddSubExpr
    ;

// *, /, //
term
    : power ((STAR | DIV | INT_DIV) power)*     # MulDivExpr
    ;

// ** (right associative)
power
    : unary (POW power)?                         # PowExpr
    ;

// unary +/-
unary
    : (PLUS | MINUS) unary                       # UnaryPMExpr
    | primary                                    # PrimaryExpr
    ;

lambda_params
    : IDENTIFIER (COMMA IDENTIFIER)*
    ;

// ======================================================
// PRIMARY EXPRESSIONS (Calls, Attributes, Indexing)
// ======================================================

primary
    : atom trailer*                              # PrimaryRoot
    ;

trailer
    : LPAREN arguments? RPAREN                   # CallTrailer
    | DOT IDENTIFIER                             # AttrTrailer
    | LBRACK expr RBRACK                         # IndexTrailer
    ;

arguments
    : argument (COMMA argument)*
    ;

argument
    : IDENTIFIER ASSIGN expr                     # KeywordArg
    | expr                                        # PositionalArg
    ;

// ======================================================
// ATOM
// ======================================================

atom
    : literal                                    # LiteralAtom
    | IDENTIFIER                                 # IdAtom
    | LPAREN expr RPAREN                         # ParenExpr
    ;

// ======================================================
// LITERALS (SET + LIST)
// ======================================================

literal
    : number_literal                             # NumberLiteral
    | string_literal                             # StringLiteral
    | boolean_literal                            # BooleanLiteral
    | NONE                                       # NoneLiteral
    | dict_literal                               # DictLiteral
    | set_literal                                # SetLiteral
    | list_literal                               # listLiteral
    ;

// Dictionary: {} or { k: v, ... }. Listed before set_literal so the presence
// of ':' disambiguates a dict from a set.
dict_literal
    : LBRACE (dict_entry (COMMA dict_entry)* COMMA?)? RBRACE
    ;

dict_entry
    : expr COLON expr
    ;

// Set: { a, b, ... } (at least one element; {} is parsed as an empty dict).
set_literal
    : LBRACE expr (COMMA expr)* COMMA? RBRACE
    ;

// List: [] , [x] , [a, b, ...]
list_literal
    : LBRACK (expr (COMMA expr)* COMMA?)? RBRACK
    ;

number_literal
    : INT                                        # IntNumber
    | FLOAT                                      # FloatNumber
    | SCIENTIFIC                                 # SciNumber
    ;

string_literal
    : TRIPLE_STRING                              # TripleString
    | SINGLE_STRING                              # SingleString
    | DOUBLE_STRING                              # DoubleString
    ;

boolean_literal
    : TRUE                                       # TrueLiteral
    | FALSE                                      # FalseLiteral
    ;

// ======================================================
// HELPERS
// ======================================================

dotted_name
    : IDENTIFIER (DOT IDENTIFIER)*
    ;
