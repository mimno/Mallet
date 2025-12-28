package cc.mallet.util;

public class QR {
    
    int numRows;
    int numCols;
    
    double[][] Q, R;

    /** 
    * Factorize the input matrix into an orthonormal matrix Q and an upper-triangular matrix R.
    * The input matrix is replaced with Q in place.
    */
    public QR(double[][] m) {
        Q = m;
        numRows = Q.length;
        
        if (numRows == 0) {
            R = null;
            return;
        }
        
        numCols = Q[0].length;
        
        double innerProduct, normalizer;
        
        R = new double[numCols][numCols];
        
        for (int col = 0; col < numCols; col++) {
            innerProduct = 0.0;
            for (int row = 0; row < numRows; row++) {
                innerProduct += Q[row][col] * Q[row][col];
            }
            
            R[col][col] = Math.sqrt(innerProduct);
            
            normalizer = 1.0 / R[col][col];
            
            for (int row = 0; row < numRows; row++) {
                Q[row][col] *= normalizer;
            }
            
            for (int otherCol = col + 1; otherCol < numCols; otherCol++) {
                innerProduct = 0.0;
                for (int row = 0; row < numRows; row++) {
                    innerProduct += Q[row][col] * Q[row][otherCol];
                }
                R[col][otherCol] = innerProduct;
                
                for (int row = 0; row < numRows; row++) {
                    Q[row][otherCol] -= Q[row][col] * innerProduct;
                }
            }
        }
    }
    
    public double[][] getQ() { return Q; }
    public double[][] getR() { return R; }
    
    
}