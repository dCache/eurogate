package eurogate.spy ;


import java.awt.* ;
import java.awt.event.* ;
import java.util.* ;
import dmg.cells.applets.login.* ;

public class      DrawingFrame
       extends    MasterPanel 
       implements ActionListener ,
                  TextListener       {

    private Button _button1 = null ;
    private Button _button2 = null ;
    private Button _button3 = null ;
    private Button _button4 = null ;
    private Button _button5 = null ;
    private Button _button6 = null ;
    private TextField _nameText = null ;
    private JumpingPigs _jumpingPigs = null ;
    
    public DrawingFrame( DomainConnection connection ){
        super( "Group Relations") ;
        setBorderSize( 20 ) ;
        setBorderColor( Color.green ) ;
    
        BorderLayout bl = new BorderLayout() ;
        bl.setHgap(10) ;
        bl.setVgap(10);
        
        Panel centerPanel = new Panel( bl ) ;
            
        Panel buttonPanel = new Panel( new RowColumnPanel(3) ) ;
        _button1 = new Button("Add Container") ;
        _button1.addActionListener(this);
        _button2 = new Button("Add Terminal") ;
        _button2.addActionListener(this);
        _button3 = new Button("Remove Element") ;
        _button3.addActionListener(this);
        _button4 = new Button("Remove All") ;
        _button4.addActionListener(this);
        _button5 = new Button("Show Progress Bar") ;
        _button5.addActionListener(this);
        _button6 = new Button("Move Progress Bar") ;
        _button6.addActionListener(this);
        _button7 = new Button("Add Relation") ;
        _button7.addActionListener(this);
        _button8 = new Button("Remove Relation") ;
        _button8.addActionListener(this);
        _button9 = new Button("Nothin'") ;
        _button9.addActionListener(this);
        buttonPanel.add( _button1 ) ;
        buttonPanel.add( _button2 ) ;
        buttonPanel.add( _button3 ) ;
        buttonPanel.add( _button4 ) ;
        buttonPanel.add( _button5 ) ;
        buttonPanel.add( _button6 ) ;
        buttonPanel.add( _button7 ) ;
        buttonPanel.add( _button8 ) ;
        buttonPanel.add( _button9 ) ;
        
        _jumpingPigs = new JumpingPigs() ;
        
        centerPanel.add( buttonPanel , "North" ) ;
        centerPanel.add( _jumpingPigs , "Center" ) ;
        
        _nameText = new TextField("");
        _nameText.addTextListener(this);
        
        centerPanel.add( _nameText , "South" ) ;
        
        add( centerPanel ) ;

    }
    public void textValueChanged(TextEvent e){   
       boolean en =  ! _nameText.getText().equals("") ;     
       _button1.setEnabled(en) ;
       _button2.setEnabled(en) ;
       _button3.setEnabled(en) ;
       _button4.setEnabled(en) ;
       _button5.setEnabled(en) ;
       _button6.setEnabled(en) ;
       _button7.setEnabled(en) ;
       _button8.setEnabled(en) ;
       _button9.setEnabled(en) ;
    }
    public void actionPerformed( ActionEvent e ){
        setMessage( "" ) ;
        Object source = e.getSource() ;
        try{
           if( source == _button1 ){
             _jumpingPigs.addContainer(_nameText.getText()) ;
           }else if( source == _button2 ){
             _jumpingPigs.addTerminal(_nameText.getText()) ;
           }else if( source == _button3 ){
             _jumpingPigs.removeItem(_nameText.getText()) ;
           }else if( source == _button4 ){
             _jumpingPigs.removeAll() ;
           }else if( source == _button5 ){
             _jumpingPigs.showProgressBar(_nameText.getText()) ;
           }else if( source == _button6 ){
             _jumpingPigs.setProgressBar(Integer.parseInt(_nameText.getText())) ;
           }else if( source == _button7 ){
           }else if( source == _button8 ){
           }else if( source == _button9 ){
           }
         }catch(Exception ee ){
           setMessage( ee.getMessage() ) ;
         }
        _nameText.setText("");
    }

}
