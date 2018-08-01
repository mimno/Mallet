package cc.mallet.types.tests;

import cc.mallet.types.MatrixOps;
import org.junit.Rule;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;

public class TestMatrixOps {
    
    public static double[] digits = new double[] { 1, 2, 3, 4, 5 };
    public static double[][] matrix = new double[][] {
      { 1, 2 },
      { 1, 2 },
      { 1, 2 },
      { 1, 2 },
      { 1, 2 }
    };
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    @Test
    public void testSum() {
        double sum = MatrixOps.sum(digits);
        assertEquals(sum, 15.0, 0.0);
    }
    
    @Test
    public void testFrobenius() {
        double diff = MatrixOps.sumSquaredDiff(matrix, matrix);
        assertEquals(diff, 0.0, 0.0);
        
        double[][] zeros = new double[][] { {0.0, 0.0}, {0.0, 0.0} };
        double[][] ones = new double[][] { {1.0, 1.0}, {1.0, 1.0} };
        
        diff = MatrixOps.sumSquaredDiff(zeros, ones);
        assertEquals(diff, 4.0, 0.0);
    }
    
    @Test
    public void saveAndLoad() {
        try {
            File savedFile = folder.newFile("matrix.txt");
            MatrixOps.savetxt(matrix, savedFile);
            double[][] loadedMatrix = MatrixOps.loadtxt(savedFile);
            
            MatrixOps.print(matrix);
            MatrixOps.print(loadedMatrix);
        
            double diff = MatrixOps.sumSquaredDiff(matrix, loadedMatrix);
        
            assertEquals(diff, 0.0, 0.0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Test
    public void matrixMultiply() {
        double[][] product = MatrixOps.aTransposeTimesB(matrix, matrix);
        
        double[][] correct = new double[][] { {5.0, 10.0}, {10.0, 20.0} };
        
        MatrixOps.print(product);
        MatrixOps.print(correct);
        
        double diff = MatrixOps.sumSquaredDiff(product, correct);
    
        assertEquals(diff, 0.0, 0.0);
    }
    
}