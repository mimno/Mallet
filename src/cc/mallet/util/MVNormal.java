package cc.mallet.util;

import java.util.Arrays;
import java.text.NumberFormat;
import cc.mallet.util.Randoms;

import cc.mallet.types.*;

/** Tools for working with multivariate normal distributions */

public class MVNormal {

	/** Simple Cholesky decomposition, with no checks on squareness, 
	 *	symmetricality, or positive definiteness. This follows the
	 *  implementation from JAMA fairly closely.
	 *  <p>
	 *  Returns L such that LL' = A and L is lower-triangular.
	 */ 
	
	public static double[] cholesky(double[] input, int numRows) {

		// Initialize the result. Note that java sets all elements to 0.
		double[] result = new double[ input.length ];
		double sumRowSquared = 0.0;
		double dotProduct = 0.0;

		// For each off-diagonal cell l_{jk} in the result,
		//  we need an index into the jth row and the kth row.
		// These are therefore both really row offsets, but one
		//  corresponds to the beginning of the (row)th row 
		//  and the other to the beginning of the (column)th row.
		int rowOffset = 0;
		int colOffset = 0;

		for (int row = 0; row < numRows; row++) {

			sumRowSquared = 0.0;
			rowOffset = row * numRows;
			
			for (int col = 0; col < row; col++) {

				dotProduct = 0.0;
				colOffset = col * numRows;

				for (int i = 0; i < col; i++) {
					dotProduct += 
						result[ rowOffset + i ] *
						result[ colOffset + i ];
				}

				result[ rowOffset + col ] = 
					(input[ rowOffset + col ] - dotProduct) /
					result[ colOffset + col ];
				sumRowSquared +=
					result[rowOffset + col] * 
					result[rowOffset + col];
			}

			//  Now the diagonal element
			result[ rowOffset + row ] =
				Math.sqrt(input[ rowOffset + row ] - sumRowSquared);
		}

		return result;
	}

	public static double[] bandCholesky(double[] input, int numRows) {

		// Initialize the result. Note that java sets all elements to 0.
		double[] result = new double[ input.length ];
		double sumRowSquared = 0.0;
		double dotProduct = 0.0;

		// For each off-diagonal cell l_{jk} in the result,
		//  we need an index into the jth row and the kth row.
		// These are therefore both really row offsets, but one
		//  corresponds to the beginning of the (row)th row 
		//  and the other to the beginning of the (column)th row.
		int rowOffset = 0;
		int colOffset = 0;

		int firstNonZero;

		for (int row = 0; row < numRows; row++) {

			sumRowSquared = 0.0;
			rowOffset = row * numRows;
			
			firstNonZero = row;

			for (int col = 0; col < row; col++) {

				if (firstNonZero == row) {
					if (input[ rowOffset + col ] == 0) {
						continue;
					}
					else {
						firstNonZero = col;
					}
				}

				dotProduct = 0.0;
				colOffset = col * numRows;

				for (int i = firstNonZero; i < col; i++) {
					dotProduct += 
						result[ rowOffset + i ] *
						result[ colOffset + i ];
				}

				result[ rowOffset + col ] = 
					(input[ rowOffset + col ] - dotProduct) /
					result[ colOffset + col ];
				sumRowSquared +=
					result[rowOffset + col] * 
					result[rowOffset + col];
			}

			//  Now the diagonal element
			result[ rowOffset + row ] =
				Math.sqrt(input[ rowOffset + row ] - sumRowSquared);
		}

		return result;
	}

	/** For testing band cholesky factorization */
	public static double[] bandMatrixRoot (int dim, int bandwidth) {
		double[] result = new double[dim * dim];
		
		for (int row = 0; row < dim; row++) {
			int rowOffset = row * dim;

			for (int col = Math.max(0, (row - bandwidth + 1));
				 col <= row;
				 col++) {
				result[rowOffset + col] = 1.0;
			}
		}

		return result;
	}

	/** Sample a multivariate normal from a precision matrix 
	 *    (ie inverse covariance matrix)
	 */
	public static double[] nextMVNormal(double[] mean, double[] precision, 
										Randoms random) {
		return nextMVNormalWithCholesky(mean, cholesky(precision, mean.length), random);
	}

	public static double[] nextMVNormalWithCholesky(double[] mean, double[] precisionLowerTriangular,
													Randoms random) {

		int n = mean.length;

		// Initialize vector z to standard normals
		//  [NB: using the same array for z and x]
		double[] result = new double[ n ];
		for (int i = 0; i < n; i++) {
			result[i] = random.nextGaussian();
		}
		
		// Now solve trans(L) x = z using back substitution
		double innerProduct;
		
		for (int i = n-1; i >= 0; i--) {
			innerProduct = 0.0;
			for (int j = i+1; j < n; j++) {
				// the cholesky decomp got us the precisionLowerTriangular triangular
				//  matrix, but we really want the transpose.
				innerProduct += result[j] * precisionLowerTriangular[ (n * j) + i ];
			}

			result[i] = (result[i] - innerProduct) / precisionLowerTriangular[ (n * i) + i ];
		}

		for (int i = 0; i < n; i++) {
			result[i] += mean[i];
		}

		return result;
	}
	
	public static double[][] nextMVNormal(int n, double[] mean, double[] precision,
										  Randoms random) {
		double[][] result = new double[n][];

		for (int i=0; i < n; i++) {
			result[i] = nextMVNormal(mean, precision, random);
		}

		return result;
	}

	public static FeatureVector nextFeatureVector(Alphabet alphabet, double[] mean,
												  double[] precision, Randoms random) {

		return new FeatureVector(alphabet, nextMVNormal(mean, precision, random));
	}

	/**
	 *  @param priorMean A vector of mean values
	 *  @param priorPrecisionDiagonal A vector representing a diagonal prior precision matrix
	 *  @param precision A precision matrix
	 */
	public static double[] nextMVNormalPosterior(double[] priorMean, double[] priorPrecisionDiagonal,
												 double[] precision,
												 double[] observedMean, int observations, 
												 Randoms random) {
		int dimension = priorMean.length;

		// Q_0 mu_0 + n Q y_bar
		double[] linearCombination = new double[dimension];

		for (int i=0; i<dimension; i++) {
			linearCombination[i] = priorMean[i] * priorPrecisionDiagonal[i];

			double innerProduct = 0.0;
			for (int j = 0; j < dimension; j++) {
				innerProduct += precision[ (dimension * i) + j ] * observedMean[j];
			}
			
			linearCombination[i] += observations * innerProduct;
		}
		
		// Q_0 + n Q
		double[] posteriorPrecision = new double[precision.length];
		
		for (int row = 0; row < dimension; row++) {
			for (int col = 0; col < dimension; col++) {
				posteriorPrecision[ (dimension * row) + col ] = 
					observations * precision[ (dimension * row) + col ];
				if (row == col) {
					posteriorPrecision[ (dimension * row) + col ] += 
						priorPrecisionDiagonal[row];
				}
			}
		}

		double[] inversePosteriorPrecision = invertSPD(posteriorPrecision, dimension);
		
		double[] posteriorMean = new double[dimension];

		for (int row = 0; row < dimension; row++) {
			double innerProduct = 0.0;
			for (int col = 0; col < dimension; col++) {
				innerProduct +=
					inversePosteriorPrecision[ (dimension * row) + col ] *
					linearCombination[ col ];
			}

			posteriorMean[row] = innerProduct;
		}

		return nextMVNormal(posteriorMean, posteriorPrecision, random);
	}

	/**
	 *  This method returns the (lower-triangular) inverse of a lower triangular
	 *   matrix.
	 */
	public static double[] invertLowerTriangular(double[] inputMatrix, int dimension) {
		double[] outputMatrix = new double[inputMatrix.length];

		double x;

		for (int row = 0; row < dimension; row++) {
			for (int col = 0; col <= row; col++) {
				// Off-diagonal elements are the negative inner product
				//  (up to the row index) of the row from input and the col
				//  from the output, divided by the diagonal from the input.
				
				if (col == row) {
					// Diagonal elements are the same, but add 1 to the numerator
					x = 1.0;
				}
				else {
					x = 0.0;
				}
				
				for (int i = col; i < row; i++) {
					x -= inputMatrix[ (dimension * row) + i ] * outputMatrix[ (dimension * i) + col ];
				}

				outputMatrix[ (dimension * row) + col ] = x / inputMatrix[ (dimension * row) + row ];
			}

		}

		return outputMatrix;
	}

	/**
	 *  Returns L'L for lower triangular matrix L.
	 */
	public static double[] lowerTriangularCrossproduct(double[] inputMatrix, int dimension) {
		
		double[] outputMatrix = new double[inputMatrix.length];

		double innerProduct;

		for (int row = 0; row < dimension; row++) {
			for (int col = row; col < dimension; col++) {

				innerProduct = 0.0;

				for (int i = col; i < dimension; i++) {
					innerProduct += inputMatrix[ row + (dimension * i) ] * inputMatrix[ col + (dimension * i) ];
				}

				outputMatrix[ (dimension * row) + col ] = innerProduct;
				outputMatrix[ row + (dimension * col) ] = innerProduct;				
			}
		}

		return outputMatrix;
	}

	/** 
	 *  Returns (lower-triangular) X = AB for square lower-triangular matrices A and B
	 */
	public static double[] lowerTriangularProduct(double[] leftMatrix, double[] rightMatrix, int dimension) {
		
		double[] outputMatrix = new double[leftMatrix.length];

        double innerProduct;

        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col <= row; col++) {

                innerProduct = 0.0;
				for (int i = col; i <= row; i++) {
					innerProduct += leftMatrix[ (dimension * row) + i ] * rightMatrix[ (dimension * i) + col ];
				}

				outputMatrix[ (dimension * row) + col ] = innerProduct;
			}
		}

		return outputMatrix;

	}

	public static double[] invertSPD(double[] inputMatrix, int dimension) {
		
		return lowerTriangularCrossproduct(invertLowerTriangular(bandCholesky(inputMatrix, dimension),
																 dimension),
										   dimension);
	}

	/**
	 *  A Wishart random variate, based on R code by Bill Venables.
	 *
	 *  @param sqrtScaleMatrix The lower-triangular matrix square root of the scale matrix.
	 *     To draw from the posterior of a precision (ie inverse covariance) matrix,
	 *     this should be chol( S^{-1} ), where S is the scatter matrix X'X of 
	 *     columns of MV normal observations X.
	 *  @param dimension The size of the matrix
	 *  @param degreesOfFreedom  The degree of freedom for the Wishart. Should be greater than dimension. For 
	 *     a posterior distribution, this is the number of observations + the df of the prior.
	 */
	public static double[] nextWishart(double[] sqrtScaleMatrix, int dimension,
									   int degreesOfFreedom, Randoms random) {

		double[] sample = new double[sqrtScaleMatrix.length];
		
		for (int row = 0; row < dimension; row++) {

			for (int col = 0; col < row; col++) {
				sample[ (row * dimension) + col ] = random.nextGaussian(0, 1);
			}
			
			sample[ (row * dimension) + row ] = Math.sqrt(random.nextChiSq(degreesOfFreedom));
		}

		//System.out.println(doubleArrayToString(sample, dimension));
		//System.out.println(doubleArrayToString(sqrtScaleMatrix, dimension));		
		//System.out.println(doubleArrayToString(lowerTriangularProduct(sample, sqrtScaleMatrix, dimension), dimension));

		System.out.println(diagonalToString(sample, dimension));
		System.out.println(diagonalToString(sqrtScaleMatrix, dimension));		
		System.out.println(diagonalToString(lowerTriangularProduct(sample, sqrtScaleMatrix, dimension), dimension));

		return lowerTriangularCrossproduct(lowerTriangularProduct(sample, sqrtScaleMatrix, dimension), dimension);

	}

	public static double[] nextWishartPosterior(double[] scatterMatrix, int observations,
												double[] priorPrecisionDiagonal, int priorDF,
												int dimension, Randoms random) {

		double[] scatterPlusPrior = new double[scatterMatrix.length];
		System.arraycopy(scatterMatrix, 0, scatterPlusPrior, 0, scatterMatrix.length);

		for (int i=0; i < dimension; i++) {
			scatterPlusPrior[ (dimension * i) + i ] += 1.0 / priorPrecisionDiagonal[i];
		}

		System.out.println(" inverted scatter plus prior");
		System.out.println(diagonalToString(invertSPD(scatterPlusPrior, dimension), dimension));

		System.out.println(" chol inverted scatter plus prior");
        System.out.println(diagonalToString(cholesky(invertSPD(scatterPlusPrior, dimension), dimension), dimension));

		double[] sqrtScaleMatrix = cholesky(invertSPD(scatterPlusPrior, dimension), dimension);
		
		return nextWishart(sqrtScaleMatrix, dimension, observations + priorDF, random);
	}
	
	/** Create a string representation of a square matrix in one-dimensional array format
	 */
	public static String doubleArrayToString(double[] matrix, int dimension) {
		NumberFormat formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(10);


		StringBuffer output = new StringBuffer();

		for (int row = 0; row < dimension; row++) {
			for (int col = 0; col < dimension; col++) {
				output.append(formatter.format(matrix[ (dimension * row) + col ]));
				output.append("\t");
			}
			output.append("\n");
		}
		
		return output.toString();
	}

	public static String diagonalToString(double[] matrix, int dimension) {
        NumberFormat formatter = NumberFormat.getInstance();
        formatter.setMaximumFractionDigits(4);


        StringBuffer output = new StringBuffer();

        for (int row = 0; row < dimension; row++) {
			output.append(formatter.format(matrix[ (dimension * row) + row ]));
			output.append(" ");
        }

        return output.toString();
    }


	public static double[] getScatterMatrix(double[][] observationMatrix) {
		int observations = observationMatrix.length;
		int dimension = observationMatrix[0].length;

		double[] outputMatrix = new double[dimension * dimension];
		double[] means = new double[dimension];

		// collect the sample means

		for (int i = 0; i < observations; i++) {
            for (int d = 0; d < dimension; d++) {
                means[d] += observationMatrix[i][d];
            }
		}

		for (int d = 0; d < dimension; d++) {
			means[d] /= observations;
		}

		// now the sample covariance (times n)

		for (int i = 0; i < observations; i++) {
            for (int d1 = 0; d1 < dimension; d1++) {
                for (int d2 = 0; d2 < dimension; d2++) {
                    outputMatrix[ (dimension * d1) + d2 ] +=
                        (observationMatrix[i][d1] - means[d1]) *
						(observationMatrix[i][d2] - means[d2]);
                }
			}
        }

		return outputMatrix;
	}

	public static void testCholesky() {

		int observations = 1000;

		double[] mean = new double[20];
		double[] precisionMatrix = new double[ 20 * 20 ];
		for (int i=0; i<20; i++) {
			precisionMatrix[ (20 * i) + i ] = 1.0;
		}

		Randoms random = new Randoms();
		double[] scatterMatrix = getScatterMatrix(nextMVNormal(observations, mean, precisionMatrix, random));
		
		double[] priorPrecision = new double[20];
		Arrays.fill(priorPrecision, 1.0);
		
		nextWishartPosterior(scatterMatrix, observations, priorPrecision, 21, 20, random);
	}


	public static void main (String[] args) {

		int dim = 100;

		double[] bandLower = bandMatrixRoot(dim, 3);
		System.out.println(doubleArrayToString(bandLower, dim));

		double[] bandMatrix = lowerTriangularCrossproduct(bandLower, dim);
		System.out.println(doubleArrayToString(bandMatrix, dim));

		long startTime;

		startTime = System.currentTimeMillis();
		for (int i=0; i<100000; i++) {
			bandCholesky(bandMatrix, dim);
		}
		System.out.println(System.currentTimeMillis() - startTime);

		startTime = System.currentTimeMillis();
		for (int i=0; i<100000; i++) {
			cholesky(bandMatrix, dim);
		}
		System.out.println(System.currentTimeMillis() - startTime);

		
		/*

		double[] l = {2.87527, 0.0, 0.0, -2.4168, 1.28, 0.0, -0.585168, -2.792234, 2.769609};
		double[] spd = { 19.133825, -1.180869, 6.403880, 
						 -1.180869,  8.895968, 1.280748,
						 6.403880,  1.280748, 9.155951 };

		double[] scatter = { 103.59761, -16.370939, 12.694755,
							 -16.37094, 106.117048,  4.079818,
							 12.69476,   4.079818, 94.065152 };

		double[] priorDiagonal = { 1.0, 1.0, 1.0 };

		testCholesky();
		*/

		//System.out.println(doubleArrayToString(nextWishartPosterior(scatter, 100, priorDiagonal, 10, 3, new Randoms()), 3));

		/*
		long startTime = System.currentTimeMillis();
		for (int i=0; i<10000; i++) {
			invertSPD(spd, 3);
		}
		System.out.println(System.currentTimeMillis() - startTime);
		*/		

		//System.out.println(doubleArrayToString(invertSPD(spd, 3), 3));
		//System.out.println(doubleArrayToString(nextWishart(l, 3, 25, new Randoms()), 3));

		/*
		double[] precisionMatrix = {0.98, -1.0, 0.0, -1.0, 2.13, -1.0, 0.0, -1.0, 1.01};
		double[] mean = new double[3];

		Randoms random = new Randoms();

		for (int i=0; i<10; i++) {
			double[] variate = nextMVNormal(mean, precisionMatrix, random);
			
			for (int j=0; j<variate.length; j++) {
				System.out.print(variate[j] + "\t");
			}
			System.out.println();
		}
		*/
		
	}
}