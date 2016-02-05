/* Copyright (C) 2002 Department of Computer Science, University of Massachusetts, Amherst

   This file is part of "MALET" (MAchine LEarning Toolkit).
   http://www.cs.umass.edu/~mccallum/malet

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License as
   published by the Free Software Foundation; either version 2 of the
   License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
   02111-1307, USA. 

   THIS SOFTWARE IS PROVIDED BY THE UNIVERSITY OF MASSACHUSETTS AND
   OTHER CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
   INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
   DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
   SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
   LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
   USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
   ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
   OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
   OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
   SUCH DAMAGE. */

/** 
   @author Aron Culotta <a href="mailto:culotta@cs.umass.edu">culotta@cs.umass.edu</a>
 */

package cc.mallet.classify.evaluate;

import java.awt.*;
import java.util.*;

import cc.mallet.classify.evaluate.*;
/**
 * Framework for standard graph. Can hold up to N data series
 */
public class Graph extends Canvas
{
	
	int top;
	int bottom;
	int left;
	int right;
	int titleHeight;
	int labelWidth;
	FontMetrics fm;
	int padding = 4;
	String title;
	String xLabel;
	String yLabel;
	int xLabelHeight;
	int yLabelWidth;
	int min;
	int max;
	int xmin;
	int xmax;
	Legend legend;
	Vector items; //2d vector - one column for each series
	
	/**
	 * Creates a Graph object
	 * @param title Title of graph
	 * @param min minimum y value
	 * @param max maximum y value
	 * @param xLabel label for x axis
	 * @param yLabel label for y axis
	 */
	public Graph(String title, int min, int max, String xLabel, String yLabel)
	{
		this.title = title;
		this.min = min;
		this.max = max;
		this.xLabel = xLabel;
		this.yLabel = yLabel;
		this.legend = new Legend();
		items = new Vector();
	}
	
	/**
	 * Set bounds of canvas
	 */
	public void setBounds(int x, int y, int width, int height) 
	{
		super.setBounds(x, y, width, height);
		fm = getFontMetrics(getFont());
		titleHeight = fm.getHeight();
		yLabelWidth = fm.stringWidth(yLabel);
		xLabelHeight = fm.getHeight();
		
		labelWidth = Math.max(fm.stringWidth(new Integer(min).toString()),
													fm.stringWidth(new Integer(max).toString())) + 2;
		top = padding + titleHeight;
		bottom = getSize().height - padding - xLabelHeight - fm.getHeight();
		left = padding + yLabelWidth;
		right = getSize().width - padding;
	} 
	
	/**
     * Paint the graph outline
     */
    public void paint(Graphics g) 
	{
	// set xmin, xmax based on item vector
		// TODO: make this user defined
		xmin=0;
		xmax=100;
		// draw the title
		fm = getFontMetrics(getFont());
		g.drawString(title, (getSize().width - fm.stringWidth(title))/2, top - padding);
		// draw the labels
		g.drawString(yLabel, 0, getSize().height/2);
		g.drawString(xLabel, (getSize().width - fm.stringWidth(xLabel))/2 ,bottom + fm.getHeight());
		// draw the max and min values
		g.drawString(new Integer(min).toString(), 
								 left - padding - fm.stringWidth(new Integer(min).toString()),
								 bottom);
		g.drawString(new Integer(max).toString(), 
								 left - padding - fm.stringWidth(new Integer(max).toString()),
								 top + titleHeight);
		g.drawString(new Integer(xmin).toString(), left, bottom + fm.getHeight());
		g.drawString(new Integer(xmax).toString(), 
								 right - fm.stringWidth(new Integer(xmax).toString()),
								 bottom + fm.getHeight());	
		// draw the vertical and horizontal lines
		g.drawLine(left, top, left, bottom);
		g.drawLine(left, bottom, right, bottom);
		// draw legend
		int legendHeight = fm.getHeight() * legend.size();
		int legendTop = bottom - legendHeight - padding - 8;
		g.drawRect((getSize().width/2)-padding, legendTop-fm.getHeight()-padding,
							 fm.stringWidth(legend.longestString())+2*padding,
							 legendHeight+2*padding);
	  for(int i=0; i<legend.size(); i++)
		{
			g.setColor(legend.color(i));
			g.drawString(legend.name(i),
									 (getSize().width)/2,
									 legendTop + i*fm.getHeight());
		}
	}   
	
	public Dimension getPreferredSize() 
	{
		return(new Dimension(500, 400));
	}
    
	
	
	/**
	 * Adds a new data series
	 * @param newItems Vector of GraphItems
	 */
	public void addItemVector(Vector newItems, String name)
	{
		items.add(newItems);
		legend.add(name);
	}
	
	public void addItem(String name, int value, Color col)
	{
		items.addElement(new GraphItem(name, value, col));
	}
	
	public void addItem(String name, int value)
	{
		items.addElement(new GraphItem(name, value, Color.black));
	}
	
	public void removeItem(String name) 
	{
		for (int i = 0; i < items.size(); i++) {
	    if (((GraphItem)items.elementAt(i)).title.equals(name))
				items.removeElementAt(i);
		}
	} 

	public class Legend
	{
		Vector series;
		Vector colors;
		public Legend()
		{
			series = new Vector();
			colors = new Vector();
		}

		public void add(String name)
		{
			series.add(name);
			if(colors.isEmpty()) //first item added
				colors.add(Color.black);
			else
			{
				float[] compArray = new float[4];
				Color prevColor = (Color)colors.get(colors.size()-1);
//				colors.add(prevColor.brighter());
				compArray = prevColor.getRGBComponents(compArray);
				compArray[3] = compArray[3] * (float).5; // halve alpha value
				colors.add(new Color(compArray[0], compArray[1],
														 compArray[2], compArray[3]));
			}
		}

		public Color color(int i)
		{
			return (Color)colors.get(i);
		}

		public String name(int i)
		{
			return (String)series.get(i);
		}
		public int size()
		{
			return colors.size();
		}
		public String longestString()
		{
			String longest = new String(""); // init to shortest string
			for(int i=0; i<series.size(); i++)
			{
				String temp = (String) series.get(i);
				if(temp.length() > longest.length())
					longest = temp;
			}
			return longest;
		}
	}
}


