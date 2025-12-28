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

/**
 * Methods for a 2-D graph
 */
public class Graph2 extends Graph
{
	int increment;
	int position;
	
	
	public Graph2(String title, int min, int max, String xLabel, String yLabel)
	{
		super(title, min, max, xLabel, yLabel);
	}
	
	public void paint(Graphics g) 
	{
		super.paint(g);
		
		
		Color temp = g.getColor();
		
		
		for (int ii = 0; ii < items.size(); ii++) 	
		{
			Vector tempV = new Vector ((Vector)items.elementAt(ii));
			GraphItem firstItem = (GraphItem)tempV.firstElement();
			int firstAdjustedValue = bottom - (((firstItem.value - min)
																					*(bottom - top))/(max - min));
			increment = (right - left)/(tempV.size() - 1);
			position = left;
//			g.setColor(firstItem.color);
			g.setColor(legend.color(ii)); // get color for this data series
			g.drawString(firstItem.title, position - fm.stringWidth(firstItem.title),
															firstAdjustedValue - 2);
			g.fillOval(position - 2, firstAdjustedValue - 2, 4, 4);
			//g.setColor(temp);
			
			for (int i = 0; i < tempV.size() - 1; i++) 
			{
				GraphItem thisItem = (GraphItem)tempV.elementAt(i);
				int thisAdjustedValue = bottom - (((thisItem.value - min)*
																					 (bottom - top))/(max - min));
				GraphItem nextItem = (GraphItem)tempV.elementAt(i+1);
				int nextAdjustedValue = bottom - (((nextItem.value - min)*
																					 (bottom - top))/(max - min));
				
				g.drawLine(position, thisAdjustedValue,
									 position+=increment, nextAdjustedValue);
				//			g.setColor(nextItem.color);
				if (nextAdjustedValue < thisAdjustedValue)
			    g.drawString(nextItem.title, position - fm.stringWidth(nextItem.title),
											 nextAdjustedValue + titleHeight + 4);
				else
			    g.drawString(nextItem.title, position - fm.stringWidth(nextItem.title),
											 nextAdjustedValue - 4);
				g.fillOval(position - 2, nextAdjustedValue - 2, 4, 4);
				//	g.setColor(temp);
			}
			g.setColor(temp);
		}
	}
}




