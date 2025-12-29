package cc.mallet.util;

import java.io.*;
import java.util.Formatter;

public class SVD {
    
    double[][] originalMatrix;
    int numRows;
    int numCols;
    
    double[][] U, V;
    double[] diagonal, offDiagonal, buffer;
    double normalizer, scale, sum;
    
    public SVD(double[][] m) {
        originalMatrix = m;
        numRows = originalMatrix.length;
        numCols = originalMatrix[0].length;
        if (numRows < numCols) {
            throw new RuntimeException("rows cannot be smaller than columns");
        }

        U = new double[numRows][numCols];
        V = new double[numCols][numCols];
        diagonal = new double[numCols];
        offDiagonal = new double[numCols];
        buffer = new double[numRows];
        
        int maxCol = numCols;
        if (numRows - 1 < maxCol) {
          maxCol = numRows - 1;
        }
        // Apply numCols Householder reflections to zero out the elements below the diagonal
        for (int col = 0; col < maxCol; col++) {
            //QR.print(originalMatrix);
            
            if (col < numRows - 1) {
                // Calculate the norm of the column at and below the diagonal
                diagonal[col] = 0.0;
                for (int row = col; row < numRows; row++) {
                    diagonal[col] += originalMatrix[row][col] * originalMatrix[row][col];
                }
                diagonal[col] = Math.sqrt(diagonal[col]);

                if (diagonal[col] != 0.0) {
                    // Maintain the sign of the original diagonal element
                    if (originalMatrix[col][col] < 0.0) {
                        diagonal[col] = -diagonal[col];
                    }

                    // Normalize the current row
                    normalizer = 1.0 / diagonal[col];
                    for (int row = col; row < numRows; row++) {
                        originalMatrix[row][col] *= normalizer;
                    }

                    // Add 1 to account for the I matrix in the Householder reflection
                    originalMatrix[col][col] += 1.0;
                }

                // And negate it.
                diagonal[col] = -diagonal[col];
            }
            
            // Now apply the same rotation to every subsequent column
            for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                if (col < numRows - 1 && diagonal[col] != 0.0) {
                    sum = 0.0;
                    for (int row = col; row < numRows; row++) {
                        sum += originalMatrix[row][otherCol] * originalMatrix[row][col];
                    }
                    
                    // originalMatrix[col][col] is now 1 + (|A[col][col]| / norm)
                    scale = sum / originalMatrix[col][col];
                    for (int row = col; row < numRows; row++) {
                        originalMatrix[row][otherCol] -= scale * originalMatrix[row][col];
                    }
                }
                
                offDiagonal[otherCol] = originalMatrix[col][otherCol];
            }
            
            // Save the values we want for U
            for (int row = col; row < numRows; row++) {
                U[row][col] = originalMatrix[row][col];
            }
            
            // Now zero out the row after the super-diagonal by applying another
            // Householder reflection going the other way.
            // Don't do this for the next-to-last column.
            if (col < numCols - 2) {
                
                // Calculate the norm of the current row after the diagonals
                sum = 0.0;
                for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                    sum += offDiagonal[otherCol] * offDiagonal[otherCol];
                }
                
                if (sum != 0.0) {
                    offDiagonal[col] = Math.sqrt(sum);
                    if (offDiagonal[col + 1] < 0.0) {
                        offDiagonal[col] = -offDiagonal[col];
                    }
                    
                    normalizer = 1.0 / offDiagonal[col];
                    for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                        offDiagonal[otherCol] *= normalizer;
                    }
                    
                    offDiagonal[col + 1] += 1.0;
                }
                
                offDiagonal[col] = -offDiagonal[col];
                
                if (col < numRows - 1 && offDiagonal[col] != 0.0) {
                    
                    // Add up the scaled columns into a single column vector.
                    for (int row = col + 1; row < numRows; row++) {
                        // First clear the buffer
                        sum = 0.0;
                        
                        // Now calculate the weighted sum
                        double[] currentRow = originalMatrix[row];
                        for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                            sum += offDiagonal[otherCol] * currentRow[otherCol];
                        }
                        
                        scale = sum / offDiagonal[col+1];
                        for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                            currentRow[otherCol] -= scale * offDiagonal[otherCol];
                        }
                    }
                    
                }
                
                for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                    V[otherCol][col] = offDiagonal[otherCol];
                }
                
            }
            
        }
        
        
        
        // Fix the last two elements in the bidiagonal matrix
        if (maxCol < numCols) {
          diagonal[maxCol] = originalMatrix[maxCol][maxCol];
        }
        offDiagonal[numCols - 2] = originalMatrix[numCols - 2][numCols - 1];
        offDiagonal[numCols - 1] = 0.0;
        
        // Prepare U by orthogonalizing from the right
        
        for (int col = maxCol - 1; col >= 0; col --) {
            if (diagonal[col] != 0.0) {
                
                // Orthogonalize each column to the current leftmost column
                for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                    sum = 0.0;
                    for (int row = col; row < numRows; row++) {
                        sum += U[row][col] * U[row][otherCol];
                    }
                    scale = -sum / U[col][col];
                    for (int row = col; row < numRows; row++) {
                        U[row][otherCol] += scale * U[row][col];
                    }
                }
                
                // Zero out above the diagonal. Is this necessary? Should it be
                //  < col?
                for (int row=0; row < col-1; row++) {
                    U[row][col] = 0.0;
                }
                U[col][col] = 1.0 - U[col][col]; // ???
                for (int row=col + 1; row < numRows; row++) {
                    U[row][col] *= -1;
                }
            }
            else {
                // identity row
                for (int row=0; row < col-1; row++) {
                    U[col][row] = 0.0;
                }
                U[col][col] = 1.0;
            }
        }

        
        // Prepare the right singular vectors V
        // Starting from the right and moving left,
        for (int col = numCols - 1; col >= 0; col --) {
            if (col < numCols - 2 && offDiagonal[col] != 0.0) {
                
                // Orthogonalize each column to the current leftmost column
                for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                    sum = 0.0;
                    for (int row = col; row < numCols; row++) {
                        sum += V[row][col] * V[row][otherCol];
                    }
                    scale = -sum / V[col+1][col];
                    for (int row = col; row < numCols; row++) {
                        V[row][otherCol] += scale * V[row][col];
                    }
                }
            }
            // Now replace that current leftmost column with an identity columns
            for (int row=0; row < numCols; row++) {
                V[row][col] = 0.0;
            }
            V[col][col] = 1.0;
        }
        
        // We now have a bidiagonal matrix represented by the two arrays `diagonal` and `offDiagonal`.
        
        int lastCol = numCols;
        double effectivelyZero = Math.pow(2.0, -966.0);
        double verySmallFraction = Math.pow(2.0, -52.0); // Values from JAMA
        
        System.out.println("starting bidiagonal QR");
        
        int iteration = 0;
        while (lastCol > 0) {
            iteration ++;
            
            //printBidiag();
            
            // Find any negligible entries on the off-diagonal
            int startCol = lastCol - 2;
            while (startCol >= 0) {
                if (Math.abs(offDiagonal[startCol]) <= effectivelyZero + verySmallFraction * (Math.abs(diagonal[startCol]) + Math.abs(diagonal[startCol + 1])) ) {
                    offDiagonal[startCol] = 0.0;
                    break;
                }
                
                startCol--;
            }
            
            // startCol could be -1 if no negligible entries.
            
            int situation;
            
            if (startCol == lastCol - 2) {
                // The last value has a zero above it! Re-sort to keep the diagonals in descending order.
                lastCol--;
                situation = 4;
                //break;
            }
            else {
                // Find any negligible diagonal entries on the diagonal
                int middleCol = lastCol - 1;
                while (middleCol >= startCol) {
                    if (middleCol == startCol) {
                        break;
                    }
                    
                    double value = Math.abs(offDiagonal[ middleCol ]);
                    if (middleCol > 0) {
                        value += Math.abs(offDiagonal[middleCol - 1]);
                    }
                    
                    else if (Math.abs(diagonal[middleCol]) <= effectivelyZero + verySmallFraction * value ) {
                        diagonal[middleCol] = 0.0;
                        break;
                    }
                    
                    middleCol --;
                }
                
                
                
                if (middleCol == startCol) {
                    // No negliglible diagonal entries!
                    situation = 3;
                }
                else if (middleCol == lastCol - 1) {
                    // A zero at the end
                    situation = 1;
                }
                else {
                    // A zero somewhere in the middle
                    situation = 2;
                }
            }
            
            // Start column could have gone negative
            startCol++;
            //System.out.format("%d %d %d\n", situation, lastCol, startCol);
            
            // JAMA notation: k = startCol, p = lastCol, s = diagonal, e = offDiagonal
            
            if (situation == 1) {
                double offDiagonalElement = offDiagonal[ lastCol - 2 ];
                offDiagonal[lastCol - 2] = 0.0;
                for (int col = lastCol - 2; col >= startCol; col--) {
                    double distance = safeDistance(diagonal[col], offDiagonalElement);
                    double cosine = diagonal[col] / distance;
                    double sine = offDiagonalElement / distance;
                    diagonal[col] = distance;
                    
                    if (col != startCol) {
                        offDiagonalElement = -sine * offDiagonal[col - 1];
                        offDiagonal[col - 1] = cosine * offDiagonal[col - 1];
                    }
                    
                    // Update U
                    for (int row = 0; row < numCols; row++) {
                        distance = cosine * V[row][col] + sine * V[row][startCol - 1];
                        V[row][startCol - 1] = -sine * V[row][startCol] + cosine * V[row][startCol - 1];
                        V[row][startCol] = distance;
                    }
                }
            }
            else if (situation == 2) {
                double offDiagonalElement = offDiagonal[ startCol -1 ];
                offDiagonal[startCol - 1] = 0.0;
                for (int col = startCol; col < lastCol; col++) {
                    double distance = safeDistance(diagonal[col], offDiagonalElement);
                    double cosine = diagonal[col] / distance;
                    double sine = offDiagonalElement / distance;
                    diagonal[col] = distance;
                    offDiagonalElement = -sine * offDiagonal[col];
                    offDiagonal[col] = cosine * offDiagonal[col];
                    
                    // Update U
                    for (int row = 0; row < numRows; row++) {
                        distance = cosine * U[row][col] + sine * U[row][startCol - 1];
                        U[row][startCol - 1] = -sine * U[row][startCol] + cosine * U[row][startCol - 1];
                        U[row][startCol] = distance;
                    }
                }
            }
            else if (situation == 3) {
                
                // Find the largest of several matrix elements, so we can normalize that
                //  element to 1.0.
                double max = Math.abs(diagonal[lastCol - 2]);
                if (Math.abs(diagonal[lastCol - 1]) > max) {
                    max = Math.abs(diagonal[lastCol - 1]);
                }
                if (Math.abs(offDiagonal[lastCol - 2]) > max) {
                    max = Math.abs(offDiagonal[lastCol - 2]);
                }
                if (Math.abs(diagonal[startCol]) > max) {
                    max = Math.abs(diagonal[startCol]);
                }
                if (Math.abs(offDiagonal[startCol]) > max) {
                    max = Math.abs(offDiagonal[startCol]);
                }
                
                // Calculate the Wilkinson shift
                // Create the values of a 2x2 matrix:
                //      [[ alpha, gamma ], [gamma, beta]]
                
                double alpha = (diagonal[lastCol - 2]/max) * (diagonal[lastCol - 2]/max);
                double beta = (diagonal[lastCol - 1]/max) * (diagonal[lastCol - 1]/max) + (offDiagonal[lastCol - 2]/max) * (offDiagonal[lastCol - 2]/max);
                double gamma = (diagonal[lastCol - 2]/max) * (offDiagonal[lastCol - 2]/max);
                
                double halfAminusB = (alpha - beta) / 2;
                double sqrtTerm = Math.sqrt(halfAminusB * halfAminusB + gamma * gamma);
                
                double shift;
                if (halfAminusB > 0) {
                    shift = halfAminusB + beta - sqrtTerm;
                }
                else {
                    shift = halfAminusB + beta + sqrtTerm;
                }
                
                // Now we calculate the first elements of the row in the squared, tridiagonal matrix
                double diagonalElement = (diagonal[startCol]/max) * (diagonal[startCol]/max) - shift;
                double offDiagonalElement = (diagonal[startCol]/max) * (offDiagonal[startCol]/max);
                double distance, sine, cosine;
                
                //System.out.format("%.4f %.4f\n", diagonalElement, offDiagonalElement);
                
                for (int col = startCol; col < lastCol - 1; col++) {
                    // Calculate the hypotenuse and evaluate SOH and CAH.
                    distance = safeDistance(diagonalElement, offDiagonalElement);
                    cosine = diagonalElement / distance;
                    sine = offDiagonalElement / distance; 
                    
                    if (col != startCol) {
                        offDiagonal[col-1] = distance;
                    }
                    
                    diagonalElement = cosine * diagonal[col] + sine * offDiagonal[col];
                    offDiagonal[col] = cosine * offDiagonal[col] - sine * diagonal[col];
                    
                    offDiagonalElement = sine * diagonal[col+1];
                    diagonal[col + 1] = cosine * diagonal[col+1];
                    
                    // Apply the rotation to V
                    /*for (int row = 0; row < numCols; row++) {
                        double temp = cosine * V[row][col] * sine * V[row][col+1];
                        V[row][col+1] = -sine * V[row][col] + cosine * V[row][col+1];
                        V[row][col] = temp;
                    }*/
                    
                    // Now rotate from the other side to kill the element below the diagonal
                
                    distance = safeDistance(diagonalElement, offDiagonalElement);
                    cosine = diagonalElement / distance;
                    sine = offDiagonalElement / distance;
                
                    diagonal[col] = distance;
                    diagonalElement = cosine * offDiagonal[col] + sine * diagonal[col+1];
                    diagonal[col+1] = -sine * offDiagonal[col] + cosine * diagonal[col+1];
                    offDiagonalElement = sine * offDiagonal[col+1];
                    offDiagonal[col+1] = cosine * offDiagonal[col+1];
                    
                    // Apply the rotation to U
                    for (int row = 0; row < numRows; row++) {
                        double temp = cosine * U[row][col] + sine * U[row][col+1];
                        U[row][col+1] = -sine * U[row][col] + cosine * U[row][col+1];
                        U[row][col] = temp;
                    }
                }
            
                offDiagonal[lastCol - 2] = diagonalElement;
            
            }
            else if (situation == 4) {
                // Make sure singular values are positive
                if (diagonal[startCol] < 0.0) {
                    diagonal[startCol] = -diagonal[startCol];
                    for (int row = 0; row < numCols; row++) {
                        V[row][startCol] = -V[row][startCol];
                    }
                }
                
                // Bubble down any singular value that's not the current largest
                int currentCol = startCol;
                while (currentCol < numCols - 1 && diagonal[currentCol] < diagonal[currentCol+1]) {
                    double temp = diagonal[currentCol];
                    diagonal[currentCol] = diagonal[currentCol+1];
                    diagonal[currentCol+1] = temp;
                    
                    // Swap columns in V
                    for (int row = 0; row < numCols; row++) {
                        temp = V[row][currentCol];
                        V[row][currentCol] = V[row][currentCol+1];
                        V[row][currentCol+1] = temp;
                    }
                    
                    // Swap columns in U
                    for (int row = 0; row < numRows; row++) {
                        temp = U[row][currentCol];
                        U[row][currentCol] = U[row][currentCol+1];
                        U[row][currentCol+1] = temp;
                    }
                    
                    currentCol++;
                }
            }
        }
        
        printBidiag();
        System.out.println();
        print(U);
        System.out.println();
        print(V);
    }
    
    public void print(double[][] matrix) {
        Formatter out = new Formatter();
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                out.format("%.4f\t", matrix[row][col]);
            }
            out.format("\n");
        }
        System.out.println(out);
    }
    
    public void printBidiag() {
        Formatter out = new Formatter();
        for (int col = 0; col < numCols; col++) {
            out.format("%.4f\t", offDiagonal[col]);
        }
        out.format("\n");
        for (int col = 0; col < numCols; col++) {
            out.format("%.4f\t", diagonal[col]);
        }
        System.out.println(out);
    }
    
    public void printFullBidiag() {
        for (int row = 0; row < numRows; row++) {
            Formatter out = new Formatter();
            for (int col = 0; col < numCols; col++) {
                if (col == row) {
                    out.format("%.4f\t", diagonal[row]);
                }
                else if (col == row + 1) {
                    out.format("%.4f\t", offDiagonal[row]);
                }
                else {
                    out.format("%.4f\t", 0.0);
                }
            }
            System.out.println(out);
        }
    }
    
    // From Jama util.Maths
    public static double safeDistance(double a, double b) {
        double r;
        if (Math.abs(a) > Math.abs(b)) {
            r = b/a;
            r = Math.abs(a)*Math.sqrt(1+r*r);
        } else if (b != 0) {
            r = a/b;
            r = Math.abs(b)*Math.sqrt(1+r*r);
        } else {
            r = 0.0;
        }
        return r;
    }
}