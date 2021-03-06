/* Generated By:JavaCC: Do not edit this line. TreeCut.java */
package eurogate.pvl.regexp.drive;

import eurogate.pvl.regexp.UnReplaceAbleString;
import eurogate.pvl.regexp.DriveExpression;
import java.util.EmptyStackException;
import java.util.Stack;

/** class TreeCut checks the syntax of a driveExpression
 *  and builds an unsolved tree.
 *  The driveExpression is stored in the class Drive as a String.
 *  The drive passes a StringReader into TreeCut. 
 *  The tree is later used by class RegFitCheck.
 */
public class TreeCut implements TreeCutConstants {
  private static final Boolean FALSE= Boolean.FALSE;
  private static final Boolean TRUE= Boolean.TRUE;
  private Stack _valueStack= new Stack();

 /** createTree() builds an unsolved tree
  *  input is gifen to its class. 
  *  output are stacked DriveExpressions.
  *  ParseException and TokenMgrError are thrown
  *  if the expression contains errors.
  */
  public DriveExpression createTree() throws ParseException,
                                             TokenMgrError,
                                             EmptyStackException{
    while(getToken(1).kind != EOF){
      driveExpression();
    }
    return (DriveExpression)_valueStack.pop();
  }

  final public void driveExpression() throws ParseException {
    jj_consume_token(LPAR);
    compare();
                               try{
                                 Object pRightValue= _valueStack.pop();
                                 Object pOpValue= _valueStack.pop();
                                 Object pLeftValue= _valueStack.pop();
                                 _valueStack.push(
                                 new DriveExpression( pLeftValue,
                                                      pOpValue,
                                                      pRightValue));
                               }catch(Exception e){
                               }
    jj_consume_token(RPAR);
  }

  final public void compare() throws ParseException {
    left();
    operation();
    right();
  }

  final public void left() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ANYNAME:
      jj_consume_token(ANYNAME);
                            _valueStack.push(
                                        new UnReplaceAbleString(token.image));
      break;
    case NAME:
      jj_consume_token(NAME);
                         _valueStack.push(token.image);
      break;
    case ZEROINT:
      jj_consume_token(ZEROINT);
                           _valueStack.push(Integer.valueOf(token.image,10) );
      break;
    case OCTINTEGER:
      jj_consume_token(OCTINTEGER);
                             _valueStack.push(
                                 Integer.valueOf(token.image.substring(1),8));
      break;
    case HEXINTEGER:
      jj_consume_token(HEXINTEGER);
                             _valueStack.push(
                                Integer.valueOf(token.image.substring(2),16));
      break;
    case PLUS:
    case MINUS:
    case FLOAT:
    case INTEGER:
      signedNumber();

      break;
    case LPAR:
      driveExpression();
      break;
    default:
      jj_la1[0] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void operation() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case AND:
      jj_consume_token(AND);
                           _valueStack.push("&&");
      break;
    case OR:
      jj_consume_token(OR);
                           _valueStack.push("||");
      break;
    case GE:
      jj_consume_token(GE);
                           _valueStack.push(">=");
      break;
    case LE:
      jj_consume_token(LE);
                           _valueStack.push("<=");
      break;
    case EQ:
      jj_consume_token(EQ);
                           _valueStack.push("==");
      break;
    case NE:
      jj_consume_token(NE);
                           _valueStack.push("!=");
      break;
    case PLUS:
      jj_consume_token(PLUS);
                           _valueStack.push("+");
      break;
    case MINUS:
      jj_consume_token(MINUS);
                           _valueStack.push("-");
      break;
    case MULT:
      jj_consume_token(MULT);
                           _valueStack.push("*");
      break;
    case DIV:
      jj_consume_token(DIV);
                           _valueStack.push("/");
      break;
    default:
      jj_la1[1] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void right() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case ANYNAME:
      jj_consume_token(ANYNAME);
                            _valueStack.push(
                                        new UnReplaceAbleString(token.image));
      break;
    case NAME:
      jj_consume_token(NAME);
                         _valueStack.push(token.image);
      break;
    case ZEROINT:
      jj_consume_token(ZEROINT);
                           _valueStack.push(Integer.valueOf(token.image,10) );
      break;
    case OCTINTEGER:
      jj_consume_token(OCTINTEGER);
                             _valueStack.push(
                                 Integer.valueOf(token.image.substring(1),8));
      break;
    case HEXINTEGER:
      jj_consume_token(HEXINTEGER);
                             _valueStack.push(
                                Integer.valueOf(token.image.substring(2),16));
      break;
    case PLUS:
    case MINUS:
    case FLOAT:
    case INTEGER:
      signedNumber();

      break;
    case LPAR:
      driveExpression();
      break;
    default:
      jj_la1[2] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  final public void signedNumber() throws ParseException {
    switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
    case PLUS:
    case FLOAT:
    case INTEGER:
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case PLUS:
        jj_consume_token(PLUS);
        break;
      default:
        jj_la1[3] = jj_gen;
        ;
      }
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case INTEGER:
        jj_consume_token(INTEGER);
                  _valueStack.push(Integer.valueOf(token.image,10) );
        break;
      case FLOAT:
        jj_consume_token(FLOAT);
                  _valueStack.push(Float.valueOf(token.image) );
        break;
      default:
        jj_la1[4] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    case MINUS:
      jj_consume_token(MINUS);
      switch ((jj_ntk==-1)?jj_ntk():jj_ntk) {
      case INTEGER:
        jj_consume_token(INTEGER);
                  _valueStack.push(Integer.valueOf("-"+ token.image,10) );
        break;
      case FLOAT:
        jj_consume_token(FLOAT);
                _valueStack.push(Float.valueOf("-"+ token.image) );
        break;
      default:
        jj_la1[5] = jj_gen;
        jj_consume_token(-1);
        throw new ParseException();
      }
      break;
    default:
      jj_la1[6] = jj_gen;
      jj_consume_token(-1);
      throw new ParseException();
    }
  }

  public TreeCutTokenManager token_source;
  ASCII_CharStream jj_input_stream;
  public Token token, jj_nt;
  private int jj_ntk;
  private int jj_gen;
  final private int[] jj_la1 = new int[7];
  final private int[] jj_la1_0 = {0x7f40180,0x7fe0,0x7f40180,0x80,0xc00000,0xc00000,0xc00180,};

  public TreeCut(java.io.InputStream stream) {
    jj_input_stream = new ASCII_CharStream(stream, 1, 1);
    token_source = new TreeCutTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 7; i++) jj_la1[i] = -1;
  }

  public void ReInit(java.io.InputStream stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 7; i++) jj_la1[i] = -1;
  }

  public TreeCut(java.io.Reader stream) {
    jj_input_stream = new ASCII_CharStream(stream, 1, 1);
    token_source = new TreeCutTokenManager(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 7; i++) jj_la1[i] = -1;
  }

  public void ReInit(java.io.Reader stream) {
    jj_input_stream.ReInit(stream, 1, 1);
    token_source.ReInit(jj_input_stream);
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 7; i++) jj_la1[i] = -1;
  }

  public TreeCut(TreeCutTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 7; i++) jj_la1[i] = -1;
  }

  public void ReInit(TreeCutTokenManager tm) {
    token_source = tm;
    token = new Token();
    jj_ntk = -1;
    jj_gen = 0;
    for (int i = 0; i < 7; i++) jj_la1[i] = -1;
  }

  final private Token jj_consume_token(int kind) throws ParseException {
    Token oldToken;
    if ((oldToken = token).next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    if (token.kind == kind) {
      jj_gen++;
      return token;
    }
    token = oldToken;
    jj_kind = kind;
    throw generateParseException();
  }

  final public Token getNextToken() {
    if (token.next != null) token = token.next;
    else token = token.next = token_source.getNextToken();
    jj_ntk = -1;
    jj_gen++;
    return token;
  }

  final public Token getToken(int index) {
    Token t = token;
    for (int i = 0; i < index; i++) {
      if (t.next != null) t = t.next;
      else t = t.next = token_source.getNextToken();
    }
    return t;
  }

  final private int jj_ntk() {
    if ((jj_nt=token.next) == null)
      return (jj_ntk = (token.next=token_source.getNextToken()).kind);
    else
      return (jj_ntk = jj_nt.kind);
  }

  private java.util.Vector jj_expentries = new java.util.Vector();
  private int[] jj_expentry;
  private int jj_kind = -1;

  final public ParseException generateParseException() {
    jj_expentries.removeAllElements();
    boolean[] la1tokens = new boolean[27];
    for (int i = 0; i < 27; i++) {
      la1tokens[i] = false;
    }
    if (jj_kind >= 0) {
      la1tokens[jj_kind] = true;
      jj_kind = -1;
    }
    for (int i = 0; i < 7; i++) {
      if (jj_la1[i] == jj_gen) {
        for (int j = 0; j < 32; j++) {
          if ((jj_la1_0[i] & (1<<j)) != 0) {
            la1tokens[j] = true;
          }
        }
      }
    }
    for (int i = 0; i < 27; i++) {
      if (la1tokens[i]) {
        jj_expentry = new int[1];
        jj_expentry[0] = i;
        jj_expentries.addElement(jj_expentry);
      }
    }
    int[][] exptokseq = new int[jj_expentries.size()][];
    for (int i = 0; i < jj_expentries.size(); i++) {
      exptokseq[i] = (int[])jj_expentries.elementAt(i);
    }
    return new ParseException(token, exptokseq, tokenImage);
  }

  final public void enable_tracing() {
  }

  final public void disable_tracing() {
  }

}
