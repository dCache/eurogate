package eurogate.misc.parser ;
import java.io.IOException ;
import java.text.ParseException ;
public interface PTokenizable {
   public PObject nextToken() throws ParseException , IOException ;
}
