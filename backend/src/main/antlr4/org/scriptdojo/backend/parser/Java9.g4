grammar Java9;

// ============================================
// PARSER RULES
// ============================================

compilationUnit
    : packageDeclaration? importDeclaration* typeDeclaration* EOF
    ;

packageDeclaration
    : 'package' qualifiedName ';'
    ;

importDeclaration
    : 'import' 'static'? qualifiedName ('.' '*')? ';'
    ;

typeDeclaration
    : classDeclaration
    | interfaceDeclaration
    | ';'
    ;

classDeclaration
    : 'public'? 'class' IDENTIFIER
      ('extends' type)?
      ('implements' typeList)?
      classBody
    ;

interfaceDeclaration
    : 'public'? 'interface' IDENTIFIER
      ('extends' typeList)?
      interfaceBody
    ;

classBody
    : '{' classBodyDeclaration* '}'
    ;

interfaceBody
    : '{' interfaceBodyDeclaration* '}'
    ;

classBodyDeclaration
    : memberDeclaration
    | constructorDeclaration
    | ';'
    ;

interfaceBodyDeclaration
    : memberDeclaration
    | ';'
    ;

memberDeclaration
    : fieldDeclaration
    | methodDeclaration
    ;

fieldDeclaration
    : modifier* type IDENTIFIER ('=' expression)? ';'
    ;

methodDeclaration
    : modifier* type IDENTIFIER formalParameters methodBody
    | modifier* 'void' IDENTIFIER formalParameters methodBody
    ;

constructorDeclaration
    : modifier* IDENTIFIER formalParameters constructorBody
    ;

formalParameters
    : '(' formalParameterList? ')'
    ;

formalParameterList
    : formalParameter (',' formalParameter)*
    ;

formalParameter
    : type IDENTIFIER
    ;

methodBody
    : block
    | ';'
    ;

constructorBody
    : '{' blockStatement* '}'
    ;

block
    : '{' blockStatement* '}'
    ;

blockStatement
    : localVariableDeclaration ';'
    | statement
    ;

localVariableDeclaration
    : type IDENTIFIER ('=' expression)?
    ;

statement
    : block
    | 'if' parExpression statement ('else' statement)?
    | 'for' '(' forControl ')' statement
    | 'while' parExpression statement
    | 'return' expression? ';'
    | statementExpression ';'
    | ';'
    ;

statementExpression
    : expression
    ;

forControl
    : forInit? ';' expression? ';' forUpdate?
    ;

forInit
    : localVariableDeclaration
    | expressionList
    ;

forUpdate
    : expressionList
    ;

parExpression
    : '(' expression ')'
    ;

expressionList
    : expression (',' expression)*
    ;

expression
    : primary
    | expression '.' IDENTIFIER
    | expression '(' expressionList? ')'
    | expression '[' expression ']'
    | expression ('++' | '--')
    | ('+'|'-'|'++'|'--') expression
    | '!' expression
    | expression ('*'|'/'|'%') expression
    | expression ('+'|'-') expression
    | expression ('<' '<' | '>' '>' '>' | '>' '>') expression
    | expression ('<=' | '>=' | '>' | '<') expression
    | expression ('==' | '!=') expression
    | expression '&' expression
    | expression '^' expression
    | expression '|' expression
    | expression '&&' expression
    | expression '||' expression
    | expression '?' expression ':' expression
    | expression ('=' | '+=' | '-=' | '*=' | '/=' | '&=' | '|=' | '^=' | '>>=' | '>>>=' | '<<=' | '%=') expression
    ;

primary
    : '(' expression ')'
    | 'this'
    | 'super'
    | literal
    | IDENTIFIER
    | type '.' 'class'
    | 'new' creator
    ;

creator
    : type classCreatorRest
    | type arrayCreatorRest
    ;

classCreatorRest
    : '(' expressionList? ')'
    ;

arrayCreatorRest
    : '[' expression ']' ('[' expression ']')* ('[' ']')*
    | ('[' ']')+ arrayInitializer
    ;

arrayInitializer
    : '{' (expression (',' expression)* (',')?)? '}'
    ;

literal
    : INTEGER_LITERAL
    | FLOAT_LITERAL
    | CHAR_LITERAL
    | STRING_LITERAL
    | BOOLEAN_LITERAL
    | NULL_LITERAL
    ;

type
    : primitiveType ('[' ']')*
    | classType ('[' ']')*
    ;

classType
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

typeList
    : type (',' type)*
    ;

primitiveType
    : 'boolean'
    | 'char'
    | 'byte'
    | 'short'
    | 'int'
    | 'long'
    | 'float'
    | 'double'
    ;

qualifiedName
    : IDENTIFIER ('.' IDENTIFIER)*
    ;

modifier
    : 'public'
    | 'protected'
    | 'private'
    | 'static'
    | 'abstract'
    | 'final'
    | 'synchronized'
    | 'volatile'
    ;

// ============================================
// LEXER RULES (Tokens)
// ============================================

// Keywords
ABSTRACT      : 'abstract';
BOOLEAN       : 'boolean';
BREAK         : 'break';
BYTE          : 'byte';
CASE          : 'case';
CATCH         : 'catch';
CHAR          : 'char';
CLASS         : 'class';
CONST         : 'const';
CONTINUE      : 'continue';
DEFAULT       : 'default';
DO            : 'do';
DOUBLE        : 'double';
ELSE          : 'else';
EXTENDS       : 'extends';
FINAL         : 'final';
FINALLY       : 'finally';
FLOAT         : 'float';
FOR           : 'for';
IF            : 'if';
IMPLEMENTS    : 'implements';
IMPORT        : 'import';
INT           : 'int';
INTERFACE     : 'interface';
LONG          : 'long';
NATIVE        : 'native';
NEW           : 'new';
PACKAGE       : 'package';
PRIVATE       : 'private';
PROTECTED     : 'protected';
PUBLIC        : 'public';
RETURN        : 'return';
SHORT         : 'short';
STATIC        : 'static';
SUPER         : 'super';
SWITCH        : 'switch';
SYNCHRONIZED  : 'synchronized';
THIS          : 'this';
THROW         : 'throw';
THROWS        : 'throws';
TRY           : 'try';
VOID          : 'void';
VOLATILE      : 'volatile';
WHILE         : 'while';

// Literals
INTEGER_LITERAL    : [0-9]+;
FLOAT_LITERAL      : [0-9]+ '.' [0-9]+ ([eE] [+-]? [0-9]+)? [fFdD]?;
CHAR_LITERAL       : '\'' (~['\\\r\n] | EscapeSequence) '\'';
STRING_LITERAL     : '"' (~["\\\r\n] | EscapeSequence)* '"';
BOOLEAN_LITERAL    : 'true' | 'false';
NULL_LITERAL       : 'null';

// Identifiers
IDENTIFIER         : [a-zA-Z_$] [a-zA-Z0-9_$]*;

// Whitespace and Comments
WS                 : [ \t\r\n]+ -> skip;
LINE_COMMENT       : '//' ~[\r\n]* -> skip;
BLOCK_COMMENT      : '/*' .*? '*/' -> skip;

// Escape sequences
fragment EscapeSequence
    : '\\' [btnfr"'\\]
    | '\\' ([0-3]? [0-7])? [0-7]
    | '\\' 'u'+ [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]
    ;