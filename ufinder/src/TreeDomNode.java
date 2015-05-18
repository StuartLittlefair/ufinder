/*==============================================================*/
/*                                                              */
/*                UK Astronomy Technology Centre                */
/*                 Royal Observatory, Edinburgh                 */
/*                   Copyright (c) PPARC 2003                   */
/*                                                              */
/*==============================================================*/
// $Id: TreeDomNode.java,v 1.1 2003/07/31 14:52:00 mfo Exp $

package ufinder;

import javax.swing.tree.DefaultMutableTreeNode;

import org.w3c.dom.Attr;
import org.w3c.dom.Comment;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

/**
 * Subclass of DefaultMutableTreeNode whith a DOM Node as user object.
 *
 * This class can be used to represent a DOM tree in a GUI as a JTree.
 * 
 */
class TreeDomNode  extends DefaultMutableTreeNode {
  private boolean _displayNodeValue = false;

  public TreeDomNode() { }

  public TreeDomNode(boolean displayNodeValue) {
    _displayNodeValue = displayNodeValue;
  }

  public TreeDomNode(Object userObject) {
    super(userObject);
  }

  public TreeDomNode(Object userObject, boolean displayNodeValue) {
    super(userObject);

    _displayNodeValue = displayNodeValue;
  }


  public String toString() {
    try {
      Node domNode = (Node)getUserObject();
      String domNodeValue = getNodeValue(domNode);

      if(_displayNodeValue && (domNodeValue != null) && (!domNodeValue.trim().equals(""))) {
        if(domNode instanceof Attr) {
          return ((Attr)domNode).getName() + ": " + domNodeValue;
        }
        else {
          return domNode.getNodeName() + ": " + domNodeValue;
        }
      }
      else {
        return domNode.getNodeName();
      }
    }
    catch(Exception e) {
      return super.toString();
    }
  }

  public static String getNodeValue(Node domNode) {
    if(domNode instanceof Comment) {
      return domNode.getNodeValue();
    }

    if(domNode instanceof Attr) {
      return ((Attr)domNode).getValue();
    }


    StringBuffer resultBuffer = new StringBuffer();

    NodeList childNodes = domNode.getChildNodes();

    if((childNodes != null) && (childNodes.getLength() > 0)) {

      for(int i = 0; i < childNodes.getLength(); i++) {
        if(childNodes.item(i) instanceof Text) {
          resultBuffer.append(childNodes.item(i).getNodeValue());
	}
      }
    }

    return resultBuffer.toString();
  }


}


