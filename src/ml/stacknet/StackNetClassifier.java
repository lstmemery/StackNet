/*Copyright (c) 2017 Marios Michailidis

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ml.stacknet;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import crossvalidation.metrics.auc;
import crossvalidation.splits.kfold;
import matrix.fsmatrix;
import matrix.smatrix;
import ml.classifier;
import ml.estimator;
import ml.Kernel.copy.KernelmodelClassifier;
import ml.Kernel.copy.KernelmodelRegressor;
import ml.LSVC.LSVC;
import ml.LSVR.LSVR;
import ml.LibFm.LibFmClassifier;
import ml.LibFm.LibFmRegressor;
import ml.LinearRegression.LinearRegression;
import ml.LogisticRegression.LogisticRegression;
import ml.NaiveBayes.NaiveBayesClassifier;
import ml.Tree.AdaboostForestRegressor;
import ml.Tree.AdaboostRandomForestClassifier;
import ml.Tree.DecisionTreeClassifier;
import ml.Tree.DecisionTreeRegressor;
import ml.Tree.GradientBoostingForestClassifier;
import ml.Tree.GradientBoostingForestRegressor;
import ml.Tree.RandomForestClassifier;
import ml.Tree.RandomForestRegressor;
import ml.knn.knnClassifier;
import ml.knn.knnRegressor;
import ml.nn.Vanilla2hnnclassifier;
import ml.nn.Vanilla2hnnregressor;
import ml.nn.multinnregressor;
import ml.nn.softmaxnnclassifier;
import preprocess.scaling.scaler;
import utilis.XorShift128PlusRandom;
import utilis.map.intint.StringIntMap4a;
import exceptions.DimensionMismatchException;
import exceptions.IllegalStateException;
import exceptions.LessThanMinimum;

/**
 * 
 * @author Marios Michailidis
 * 
 * <H2> INTRODUCTION TO STACKNET </H2>
 * 
 * <p>implements STACKNET for classification given any number of input classifiers and/or regressors.
 * STACKNET uses stacked generalization into a feedforward neural network architecture to ensemble multiple models in classification problems.
 * <H2> THE LOGIC </H2>
 *<p>Given some input data , a neural network normally applies a perceptron along with a transformation function like relu or sigmoid, tanh or others. The equation is often expressed as
 *<p> f<sub>1</sub> (x<sub>i</sub> )=∑<sup>H</sup><sub>(h=1)</sub>( g<sub>1</sub> ((x<sub>i</sub> ) ̂)beta<sub>1h</sub>+bias<sub>1h</sub>) </p>
 *<p> The STACKNET model assumes that this function can take the form of any supervised machine learning algorithm - or in other words:
 *<p> f<sub>1</sub> (x<sub>i</sub>)=∑<sup>H</sup><sub>(h=1)</sub>( g<sub>1</sub> s<sub>h</sub> ((x<sub>i</sub> )̂ )) 
 * <p> where s expresses this machine learning model and g is a linear function.
 * <p> Logically the outputs of each neuron , can be fed onto next layers. For instance in teh second layer the equation will be :
 * <p> f<sub>2</sub> (x<sub>i</sub>)=∑<sup>H2</sup><sub>(h2=1)</sub>( (f<sub>1</sub> ((x<sub>i</sub> )̂))a<sub>h2</sub>)
 * <p> Where m is one of the H2 algorithms included in the second layer and can be any estimator, classifier or regressor</p>
 * <p> The aforementioned formula could be generalised as follows for any layer:
 * <p> f<sub>n</sub> (x<sub>i</sub>)=∑<sup>H</sup><sub>(h=1)</sub>( (f<sub>(n-1)</sub> ((x<sub>i</sub>)̂ )) a<sub>h</sub>)
 * <p> Where a is the jth algorithm out of Hn in the nth hidden model layer and f_(n-1) the previous model’s raw score prediction in respect to the target variable.
 * <p> To create an output prediction score for any number of unique categories of the response variable, all selected algorithms in the last layer need to have output’s dimensionality equal to the number those unique classes
 * In case where there are many such classifiers, the results is the scaled average of all these output predictions and can be written as:
 * <p> f<sub>n</sub> (x<sub>i</sub>)=1/C ∑<sup>C</sup><sub>(c=1)</sub>∑<sup>H</sup><sub>(h=1)</sub>( (f<sub>(n-1)</sub> ((x<sub>i</sub>)̂ )) a<sub>h</sub>)
 * <p> Where C is the number of unique classifiers in the last layer. In case of just 1 classifier in the output layer this would resemble the softmax activation function of a typical neural network used for classification. 
 * <H2> THE MODES </H2>
 * <p>The <em>stacking</em> element of the StackNet model could be run with 2 different modes. The first mode (e.g. the default) is the one already mentioned and assumes that in each layer uses the predictions (or output scores) of the direct previous one similar with a typical feedforward neural network or equivalently:
 * <p><b> Normal stacking mode</b> 
 * <p>f<sub>n</sub> (x<sub>i</sub>)=∑<sup>H</sup><sub>(h=1)</sub>(f<sub>(n-1)</sub> ((x<sub>i</sub> )̂ )) a<sub>h</sub>
 * <p>The second mode (also called restacking) assumes that each layer uses previous neurons activations as well as all previous layers’ neurons (including the input layer). Therefore the previous formula can be re-written as:
 * <p><b> Restacking mode</b> 
 * <p> f<sub>n</sub> (x<sub>i</sub>)=∑<sup>H</sup><sub>(h=1)</sub>∑<sub>(k=1)</sub><sup>(K=n-1)</sup>(f<sub>k</sub> ((x<sub>i</sub>)̂ )) a<sub>k</sub>
 * <p> Assuming the algorithm is located in layer n>1, to activate each neuron h in that layer, all outputs from all neurons from the previous n-1
 *  (or k) layers need to be accumulated (or stacked .The intuition behind this mode is drive from the fact that the higher level algorithm have extracted information from the input data, but rescanning the input space may yield new information not obvious from the first passes. This is also driven from the forward training methodology discussed below and assumes that convergence needs to happen within one model iteration
 * <H2> K-FOLD TRAINING</H2>
 * <p>The typical neural networks are most commonly trained with a form of back propagation, however stacked generalization requites a forward training methodology that splits the data into two parts – one of which is used for training and the other for predictions. The reason this split is necessary is to avoid the over fitting that could be a factor of the kind of algorithms being used as inputs as well as the absolute count of them.
 * <p> However splitting the data in just 2 parts would mean that in each new layer the second part needs to be further dichotomized increasing the bias of overfitting even more as each algorithm will have to be trained and validated on increasingly less data. 
To overcome this drawback the algorithm utilises a k-fold cross validation (where k is a hyper parameter) so that all the original training data is scored in different k batches thereby outputting n shape training predictions where n is the size of the samples in the training data. Therefore the training process is consisted of 2 parts:
<p> 1. Split the data k times and run k models to output predictions for each k part and then bring the k parts back together to the original order so that the output predictions can be used in later stages of the model. This process is illustrated below : 
<p> 2. Rerun the algorithm on the whole training data to be used later on for scoring the external test data. There is no reason to limit the ability of the model to learn using 100% of the training data since the output scoring is already unbiased (given that it is always scored as a holdout set).
<p> It should be noted that (1) is only applied during training to create unbiased predictions for the second layers’s model to fit one. During scoring time (and after model training is complete) only (2) is in effect.
<p>. All models must be run sequentially based on the layers, but the order of the models within the layer does not matter. In other words all models of layer one need to be trained in order to proceed to layer two but all models within the layer can be run asynchronously and in parallel to save time.  
 The k-fold may also be viewed as a form of regularization where smaller number of folds (but higher than 1) ensure that the validation data is big enough to demonstrate how well a single model could generalize. On the other hand higher k means that the models come closer to running with 100% of the training and may yield more unexplained information. The best values could be found through cross validation. 
Another possible way to implement this could be to save all the k models and use the average of their predicting to score the unobserved test data, but this have all the models never trained with 100% of the training data and may be suboptimal. 
 * <H3> Final Notes</H3>
 * <p> STACKNET is commonly a better than the best single model it contains, however its ability to perform well still relies on a mix of string and diverse single models in order to get  the best out of this meta-modelling methodology.
 * 
 */
public class StackNetClassifier implements estimator,classifier, Serializable {

	/**
	 * list of classifiers to build for different levels
	 */
	private estimator[][]  tree_body ;
	
	/**
	 * column counts of each level
	 */
	private int column_counts[];
	/**
	 * list of classifiers's parameters to build for different levels
	 */
	public  String[] [] parameters ;
	/**
	 * threads to use
	 */
	public int threads=1;
	
	/**
	 * Print datasets after each level if True
	 */
	public boolean print=false;
	/**
	 * Suffix for output files
	 */
	public String output_name="stacknet";
	/**
	 * The metric to validate the results on. can be either logloss,  accuracy or auc (for binary only)
	 */
	public String metric="logloss";	
	/**
	 * stack the previous level data
	 */
	public boolean stackdata=false;
	
	/**
	 * number of kfolds to run cv for
	 */
	public int folds=5;

	
	public  estimator[][] Get_tree(){
		if (this.tree_body==null || this.tree_body.length<=0){
			throw new IllegalStateException(" There is NO tree" );
		}
		return tree_body;
	}
    /**
     * seed to use
     */
	public int seed=1;
	
	/**
	 * Random number generator to use
	 */
	private Random random;
	/**
	 * weighst to used per row(sample)
	 */
	public double [] weights;
	/**
	 * if true, it prints stuff
	 */
	public boolean verbose=true;
	/**
	 * Target variable in double format
	 */
	public double target[];
	/**
	 * Target variable in 2d double format
	 */	
	public double target2d[][];
	/**
	 * Target variable in fixed-size matrix format
	 */	
	public int [] fstarget;	
	/**
	 * Target variable in sparse matrix format
	 */	
	public smatrix starget;	
	/**
	 * Hold feature importance for the tree
	 */
	 double feature_importances [];
	/**
	 * How many predictors the model has
	 */
	private int columndimension=0;
	//return number of predictors in the model
	public int get_predictors(){
		return columndimension;
	}
	/**
	 * Number of target-variable columns. The name is left as n_classes(same as classification for consistency)
	 */
	private int n_classes=0;
	/**
	 * Name of the unique classes
	 */
	private String classes[];
	/**
	 * Target variable in String format
	 */	
	public String Starget[];
	
	public int getnumber_of_classes(){
		return n_classes;
	}
	@Override
	public String[] getclasses() {
		if (classes==null || classes.length<=0){
			throw new  IllegalStateException (" No classes are found, the model needs to be fitted first");
		} else {
		return classes;
		}
	}
	@Override
	public void AddClassnames(String[] names) {
		
		String distinctnames[]=manipulate.distinct.distinct.getstringDistinctset(names);
		if (distinctnames.length<2){
			throw new LessThanMinimum(names.length,2);
		}
		if (distinctnames.length!=names.length){
			throw new  IllegalStateException (" There are duplicate values in the names of the addClasses method, dedupe before adding them");
		}
		classes=new String[names.length];
		for (int j=0; j < names.length; j++){
			classes[j]=names[j];
		}
	}
	/**
	 * The object that holds the modelling data in double form in cases the user chooses this form
	 */
	private double dataset[][];
	/**
	 * The object that holds the modelling data in fsmatrix form cases the user chooses this form
	 */
	private fsmatrix fsdataset;
	/**
	 * The object that holds the modelling data in smatrix form cases the user chooses this form
	 */
	private smatrix sdataset;	
	/**
	 * Default constructor for LinearRegression with no data
	 */
	public StackNetClassifier(){
	
	}	
	/**
	 * Default constructor for LinearRegression with double data
	 */
	public StackNetClassifier(double data [][]){
		
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		dataset=data;		
	}
	
	/**
	 * Default constructor for LinearRegression with fsmatrix data
	 */
	public StackNetClassifier(fsmatrix data){
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		fsdataset=data;
	}
	/**
	 * Default constructor for LinearRegression with smatrix data
	 */
	public StackNetClassifier(smatrix data){
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		sdataset=data;
	}

	public void setdata(double data [][]){
		
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		dataset=data;		
	}

	public void setdata(fsmatrix data){
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		fsdataset=data;
	}

	public void setdata(smatrix data){
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		sdataset=data;
		}
		
	@Override
	public void run() {
		// check which object was chosen to train on
		if (dataset!=null){
			this.fit(dataset);
		} else if (fsdataset!=null){
			this.fit(fsdataset);	
		} else if (sdataset!=null){
			this.fit(sdataset);	
		} else {
			throw new IllegalStateException(" No data structure specifed in the constructor" );			
		}	
	}		
	

	/**
	 * default Serial id
	 */
	private static final long serialVersionUID = -8611561535854392960L;
	@Override
	public double[][] predict_proba(double[][] data) {
		 
		/*  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 ||this.tree_body==null || this.tree_body.length<=0 ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}  
	
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data[0].length!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data[0].length);	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}	
		double predictions[][]= new double [data.length][this.n_classes];
		fsmatrix arrays =null;
		
		for(int level=0; level<tree_body.length; level++){
			int column_counter=0;
			arrays= new fsmatrix(predictions.length, this.column_counts[level]);
			for (estimator k : tree_body[level]){
				double preds[][]=k.predict_proba(data);
				if (preds[0].length==2 && level <tree_body.length-1){
					preds=manipulate.select.columnselect.ColumnSelect(preds, new int [] {1});
				}
				for (int j=0; j <preds[0].length; j++ ){
					for (int i=0; i <preds.length; i++ ){
						arrays.SetElement(i, column_counter, preds[i][j]);
					}
					column_counter+=1;
				}				
			}
			
			if (this.stackdata){
				
				double temp[][] = new double [data.length][data[0].length+arrays.GetColumnDimension()];
				int ccc=0;
				for (int i=0; i <data.length; i++ ){ 
					ccc=0;
					for (int j=0; j <data[0].length; j++ ){
						temp[i][ccc]=data[i][j];
						ccc++;
					}
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						temp[i][ccc]=arrays.GetElement(i, j);
						ccc++;
					}
				}
				
				data=temp;	
			}
			else {
				int ccc=0;
				 data =new double [data.length][arrays.GetColumnDimension()] ;
				 for (int i=0; i <data.length; i++ ){ 
						ccc=0;
						for (int j=0; j <arrays.GetColumnDimension(); j++ ){
							data[i][ccc]=arrays.GetElement(i, j);
							ccc++;
						}
					}					
				
			}
			
			if (this.print){
				
				if (this.verbose){
					
					System.out.println("Printing reusable test for level: " + (level+1) + " as : " + this.output_name +"_test" +  (level+1)+ ".csv");
				}
				arrays.ToFile(this.output_name +"_test" +  (level+1)+ ".csv");
				
			}		
			
		}
		
		if (arrays.GetColumnDimension()%this.n_classes!=0){
			 throw new IllegalStateException("Number of final model's output columns need to be a factor of the used classes");  
		}
		int multi=arrays.GetColumnDimension()/this.n_classes;
		
			for (int i=0; i <predictions.length; i++ ){
				for (int m=0; m <multi; m++ ){				
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						int col=arrays.GetColumnDimension() * m + j ;
						predictions[i][j]+=arrays.GetElement(i, col);
					}
				}
			}
		
			scale_scores(predictions);
		
			// return the 1st prediction
			return predictions;
			
			}

	@Override
	public double[][] predict_proba(fsmatrix data) {
		if (n_classes<2 ||this.tree_body==null || this.tree_body.length<=0 ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}  
	
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}	
		
		double predictions[][]= new double [data.GetRowDimension()][this.n_classes];
		fsmatrix arrays =null;
		
		for(int level=0; level<tree_body.length; level++){
			int column_counter=0;
			arrays= new fsmatrix(predictions.length, this.column_counts[level]);
			for (estimator k : tree_body[level]){
				double preds[][]=k.predict_proba(data);
				if (preds[0].length==2 && level <tree_body.length-1){
					preds=manipulate.select.columnselect.ColumnSelect(preds, new int [] {1});
				}
				for (int j=0; j <preds[0].length; j++ ){
					for (int i=0; i <preds.length; i++ ){
						arrays.SetElement(i, column_counter, preds[i][j]);
					}
					column_counter+=1;
				}				
			}
			
			if (this.stackdata){
				
				
				double temp[][] = new double [data.GetRowDimension()][data.GetColumnDimension()+arrays.GetColumnDimension()];
				int ccc=0;
				for (int i=0; i <data.GetRowDimension(); i++ ){ 
					ccc=0;
					for (int j=0; j <data.GetColumnDimension(); j++ ){
						temp[i][ccc]=data.GetElement(i, j);
						ccc++;
					}
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						temp[i][ccc]=arrays.GetElement(i, j);
						ccc++;
					}
				}
				
				data=new fsmatrix(temp);	
			}
			else {
				int ccc=0;
				 data =new fsmatrix(data.GetRowDimension(),arrays.GetColumnDimension());
				 for (int i=0; i <data.GetRowDimension(); i++ ){ 
						ccc=0;
						for (int j=0; j <arrays.GetColumnDimension(); j++ ){
							data.SetElement(i, ccc, arrays.GetElement(i, j));
							ccc++;
						}
					}					
				
			}
			if (this.print){
				
				if (this.verbose){
					
					System.out.println("Printing reusable test for level: " + (level+1) + " as : " + this.output_name +"_test" +  (level+1)+ ".csv");
				}
				arrays.ToFile(this.output_name +"_test" +  (level+1)+ ".csv");
				
			}	
			
		}
		
		if (arrays.GetColumnDimension()%this.n_classes!=0){
			 throw new IllegalStateException("Number of final model's output columns need to be a factor of the used classes");  
		}
		int multi=arrays.GetColumnDimension()/this.n_classes;
		
			for (int i=0; i <predictions.length; i++ ){
				for (int m=0; m <multi; m++ ){				
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						int col=arrays.GetColumnDimension() * m + j ;
						predictions[i][j]+=arrays.GetElement(i, col);
					}
				}
			}
		
			scale_scores(predictions);
		

			// return the 1st prediction
			return predictions;
		
			}

	@Override
	public double[][] predict_proba(smatrix data) {
		
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 ||this.tree_body==null || this.tree_body.length<=0 ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}  

		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}
		if (!data.IsSortedByRow()){
			data.convert_type();
		}
		if (data.indexer==null){
			data.buildmap();;
		}
		
		this.stackdata=false;
		double predictions[][]= new double [data.GetRowDimension()][this.n_classes];
		fsmatrix arrays =null;
		
		for(int level=0; level<tree_body.length; level++){
			int column_counter=0;
			arrays= new fsmatrix(predictions.length, this.column_counts[level]);
			for (estimator k : tree_body[level]){
				double preds[][]=k.predict_proba(data);
				if (preds[0].length==2 && level <tree_body.length-1){
					preds=manipulate.select.columnselect.ColumnSelect(preds, new int [] {1});
				}
				for (int j=0; j <preds[0].length; j++ ){
					for (int i=0; i <preds.length; i++ ){
						arrays.SetElement(i, column_counter, preds[i][j]);
					}
					column_counter+=1;
				}				
			}
			
			if (this.stackdata){
				
				
				double temp[][] = new double [data.GetRowDimension()][data.GetColumnDimension()+arrays.GetColumnDimension()];
				int ccc=0;
				for (int i=0; i <data.GetRowDimension(); i++ ){ 
					ccc=0;
					for (int j=0; j <data.GetColumnDimension(); j++ ){
						temp[i][ccc]=data.GetElement(i, j);
						ccc++;
					}
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						temp[i][ccc]=arrays.GetElement(i, j);
						ccc++;
					}
				}
				
				data=new smatrix(temp);	
			}
			else {

				data=new smatrix(arrays);
					
				
			}
			
			if (this.print){
				
				if (this.verbose){
					
					System.out.println("Printing reusable test for level: " + (level+1) + " as : " + this.output_name +"_test" +  (level+1)+ ".csv");
				}
				arrays.ToFile(this.output_name +"_test" +  (level+1)+ ".csv");
				
			}	
		}
		
		if (arrays.GetColumnDimension()%this.n_classes!=0){
			 throw new IllegalStateException("Number of final model's output columns need to be a factor of the used classes");  
		}
		int multi=arrays.GetColumnDimension()/this.n_classes;
		
			for (int i=0; i <predictions.length; i++ ){
				for (int m=0; m <multi; m++ ){				
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						int col=arrays.GetColumnDimension() * m + j ;
						predictions[i][j]+=arrays.GetElement(i, col);
					}
				}
			}
		
			scale_scores(predictions);
		

			// return the 1st prediction
			return predictions;
	}

	@Override
	public double[] predict_probaRow(double[] data) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 || this.tree_body==null || this.tree_body.length<=0  ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}   
		
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.length!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as the trained one: " +  columndimension + " <> " + data.length);	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
		}	

		
		double predictions[]= new double [this.n_classes];


			// return the 1st prediction
			return predictions;
			}


	@Override
	public double[] predict_probaRow(fsmatrix data, int rows) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 || this.tree_body==null || this.tree_body.length<=0  ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}   
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
		}	

		double predictions[]= new double [this.n_classes];


		
		// return the 1st prediction
		return predictions;		
			
	}

	@Override
	public double[] predict_probaRow(smatrix data, int start, int end) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 || this.tree_body==null || this.tree_body.length<=0  ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}   
			
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
		}	
		double predictions[]= new double [this.n_classes];

		// return the 1st prediction
		return predictions;
			}

	@Override
	public double[] predict(fsmatrix data) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 ||this.tree_body==null || this.tree_body.length<=0 ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}  

		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}	
		
		double predictionsclass[]= new double [data.GetRowDimension()];
		double predictions[][]= new double [data.GetRowDimension()][this.n_classes];
		fsmatrix arrays =null;
		
		for(int level=0; level<tree_body.length; level++){
			int column_counter=0;
			arrays= new fsmatrix(predictions.length, this.column_counts[level]);
			for (estimator k : tree_body[level]){
				double preds[][]=k.predict_proba(data);
				if (preds[0].length==2 && level <tree_body.length-1){
					preds=manipulate.select.columnselect.ColumnSelect(preds, new int [] {1});
				}
				for (int j=0; j <preds[0].length; j++ ){
					for (int i=0; i <preds.length; i++ ){
						arrays.SetElement(i, column_counter, preds[i][j]);
					}
					column_counter+=1;
				}				
			}
			
			if (this.stackdata){
				
				
				double temp[][] = new double [data.GetRowDimension()][data.GetColumnDimension()+arrays.GetColumnDimension()];
				int ccc=0;
				for (int i=0; i <data.GetRowDimension(); i++ ){ 
					ccc=0;
					for (int j=0; j <data.GetColumnDimension(); j++ ){
						temp[i][ccc]=data.GetElement(i, j);
						ccc++;
					}
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						temp[i][ccc]=arrays.GetElement(i, j);
						ccc++;
					}
				}
				
				data=new fsmatrix(temp);	
			}
			else {
				int ccc=0;
				 data =new fsmatrix(data.GetRowDimension(),arrays.GetColumnDimension());
				 for (int i=0; i <data.GetRowDimension(); i++ ){ 
						ccc=0;
						for (int j=0; j <arrays.GetColumnDimension(); j++ ){
							data.SetElement(i, ccc, arrays.GetElement(i, j));
							ccc++;
						}
					}					
				
			}
			
			
		}
		
		if (arrays.GetColumnDimension()%this.n_classes!=0){
			 throw new IllegalStateException("Number of final model's output columns need to be a factor of the used classes");  
		}
		int multi=arrays.GetColumnDimension()/this.n_classes;
		
			for (int i=0; i <predictions.length; i++ ){
				for (int m=0; m <multi; m++ ){				
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						int col=arrays.GetColumnDimension() * m + j ;
						predictions[i][j]+=arrays.GetElement(i, col);
					}
				}
			}
		


		
			// return the 1st prediction

		for (int i=0; i < predictionsclass.length; i++) {
			double temp[]=predictions[i];
	    	  int maxi=0;
	    	  double max=temp[0];
	    	  for (int k=1; k<n_classes; k++) {
	    		 if (temp[k]>max){
	    			 max=temp[k];
	    			 maxi=k;	 
	    		 }
	    	  }
	    	  try{
	    		  predictionsclass[i]=Double.parseDouble(classes[maxi]);
	    	  } catch (Exception e){
	    		  predictionsclass[i]=maxi;
	    	  }

		}		
		
		predictions=null;

			// return the 1st prediction
			return predictionsclass;
			
			}
			

	@Override
	public double[] predict(smatrix data) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 ||this.tree_body==null || this.tree_body.length<=0 ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}  

		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}
		if (!data.IsSortedByRow()){
			data.convert_type();
		}
		if (data.indexer==null){
			data.buildmap();
		}
		this.stackdata=false;
		double predictionsclass[]= new double [data.GetRowDimension()];
		double predictions[][]= new double [data.GetRowDimension()][this.n_classes];
		fsmatrix arrays =null;
		
		for(int level=0; level<tree_body.length; level++){
			int column_counter=0;
			arrays= new fsmatrix(predictions.length, this.column_counts[level]);
			for (estimator k : tree_body[level]){
				double preds[][]=k.predict_proba(data);
				if (preds[0].length==2 && level <tree_body.length-1){
					preds=manipulate.select.columnselect.ColumnSelect(preds, new int [] {1});
				}
				for (int j=0; j <preds[0].length; j++ ){
					for (int i=0; i <preds.length; i++ ){
						arrays.SetElement(i, column_counter, preds[i][j]);
					}
					column_counter+=1;
				}				
			}
			
			if (this.stackdata){
				
				
				double temp[][] = new double [data.GetRowDimension()][data.GetColumnDimension()+arrays.GetColumnDimension()];
				int ccc=0;
				for (int i=0; i <data.GetRowDimension(); i++ ){ 
					ccc=0;
					for (int j=0; j <data.GetColumnDimension(); j++ ){
						temp[i][ccc]=data.GetElement(i, j);
						ccc++;
					}
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						temp[i][ccc]=arrays.GetElement(i, j);
						ccc++;
					}
				}
				
				data=new smatrix(temp);	
			}
			else {
				data=new smatrix(arrays);	
				
				
			}
			
			
		}
		
		if (arrays.GetColumnDimension()%this.n_classes!=0){
			 throw new IllegalStateException("Number of final model's output columns need to be a factor of the used classes");  
		}
		int multi=arrays.GetColumnDimension()/this.n_classes;
		
			for (int i=0; i <predictions.length; i++ ){
				for (int m=0; m <multi; m++ ){				
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						int col=arrays.GetColumnDimension() * m + j ;
						predictions[i][j]+=arrays.GetElement(i, col);
					}
				}
			}
		


		
			// return the 1st prediction

		for (int i=0; i < predictionsclass.length; i++) {
			double temp[]=predictions[i];
	    	  int maxi=0;
	    	  double max=temp[0];
	    	  for (int k=1; k<n_classes; k++) {
	    		 if (temp[k]>max){
	    			 max=temp[k];
	    			 maxi=k;	 
	    		 }
	    	  }
	    	  try{
	    		  predictionsclass[i]=Double.parseDouble(classes[maxi]);
	    	  } catch (Exception e){
	    		  predictionsclass[i]=maxi;
	    	  }

		}		
		
		predictions=null;

			// return the 1st prediction
			return predictionsclass;
			
	}

	@Override
	public double[] predict(double[][] data) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 ||this.tree_body==null || this.tree_body.length<=0 ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}  
	
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data[0].length!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data[0].length);	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}	
		
		double predictionsclass[]= new double [data.length];
		double predictions[][]= new double [data.length][n_classes];

		fsmatrix arrays =null;
		
		for(int level=0; level<tree_body.length; level++){
			int column_counter=0;
			arrays= new fsmatrix(predictions.length, this.column_counts[level]);
			for (estimator k : tree_body[level]){
				double preds[][]=k.predict_proba(data);
				if (preds[0].length==2 && level <tree_body.length-1){
					preds=manipulate.select.columnselect.ColumnSelect(preds, new int [] {1});
				}
				for (int j=0; j <preds[0].length; j++ ){
					for (int i=0; i <preds.length; i++ ){
						arrays.SetElement(i, column_counter, preds[i][j]);
					}
					column_counter+=1;
				}				
			}
			
			if (this.stackdata){
				
				double temp[][] = new double [data.length][data[0].length+arrays.GetColumnDimension()];
				int ccc=0;
				for (int i=0; i <data.length; i++ ){ 
					ccc=0;
					for (int j=0; j <data[0].length; j++ ){
						temp[i][ccc]=data[i][j];
						ccc++;
					}
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						temp[i][ccc]=arrays.GetElement(i, j);
						ccc++;
					}
				}
				
				data=temp;	
			}
			else {
				int ccc=0;
				 data =new double [data.length][arrays.GetColumnDimension()] ;
				 for (int i=0; i <data.length; i++ ){ 
						ccc=0;
						for (int j=0; j <arrays.GetColumnDimension(); j++ ){
							data[i][ccc]=arrays.GetElement(i, j);
							ccc++;
						}
					}					
				
			}
			
			
		}
		
		if (arrays.GetColumnDimension()%this.n_classes!=0){
			 throw new IllegalStateException("Number of final model's output columns need to be a factor of the used classes");  
		}
		int multi=arrays.GetColumnDimension()/this.n_classes;
		
			for (int i=0; i <predictions.length; i++ ){
				for (int m=0; m <multi; m++ ){				
					for (int j=0; j <arrays.GetColumnDimension(); j++ ){
						int col=arrays.GetColumnDimension() * m + j ;
						predictions[i][j]+=arrays.GetElement(i, col);
					}
				}
			}
		
			// return the 1st prediction

		for (int i=0; i < predictionsclass.length; i++) {
			double temp[]=predictions[i];
	    	  int maxi=0;
	    	  double max=temp[0];
	    	  for (int k=1; k<n_classes; k++) {
	    		 if (temp[k]>max){
	    			 max=temp[k];
	    			 maxi=k;	 
	    		 }
	    	  }
	    	  try{
	    		  predictionsclass[i]=Double.parseDouble(classes[maxi]);
	    	  } catch (Exception e){
	    		  predictionsclass[i]=maxi;
	    	  }

		}		
			
		predictions=null;

			// return the 1st prediction
			return predictionsclass;


			
			}
	@Override
	public double predict_Row(double[] data) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<1 || this.tree_body==null || this.tree_body.length<=0  ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}   		
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.length!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as the trained one: " +  columndimension + " <> " + data.length);	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
		}	


		double predictions= 0.0;
	

			// return the 1st prediction
			return predictions;
			}
	
	@Override
	public double predict_Row(fsmatrix data, int rows) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<2 || this.tree_body==null || this.tree_body.length<=0  ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}   
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
		}	


		double predictions= 0.0;
	

			// return the 1st prediction
			return predictions;
			}
			
	

	@Override
	public double predict_Row(smatrix data, int start, int end) {
		/*
		 *  check if the Create_Logic method is run properly
		 */
		if (n_classes<1 || this.tree_body==null || this.tree_body.length<=0  ){
			 throw new IllegalStateException("The fit method needs to be run successfully in " +
										"order to create the logic before attempting scoring a new set");}   
		
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" There is nothing to score" );
		}
		if (data.GetColumnDimension()!=columndimension){
			throw new IllegalStateException(" Number of predictors is not the same as th4 trained one: " +  columndimension + " <> " + data.GetColumnDimension());	
		}		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
		}	

		double predictions= 0.0;
	


			// return the 1st prediction
			return predictions;
			}

	
	
	@Override
	public void fit(double[][] data) {
		// make sensible checks
		if (data==null || data.length<=0){
			throw new IllegalStateException(" Main data object is null or has too few cases" );
		}
		dataset=data;
		columndimension=data[0].length;
		if (this.parameters.length<1 || (this.parameters[0].length<1) ){
			throw new IllegalStateException(" Parameters need to be provided in string format as model_name parameter_m:value_n ... " );
		}
		if (parameters.length<2 && parameters[0].length==1){
			throw new IllegalStateException("StackNet cannot have only 1 model" );
		}


		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}
		if ( !metric.equals("logloss")  && !metric.equals("accuracy") && !metric.equals("auc")){
			throw new IllegalStateException(" The metric to validate on needs to be one of logloss, accuracy or auc (for binary only) " );	
		}
		// make sensible checks on the target data
		if ( (target==null || target.length!=data.length)  ){
			throw new IllegalStateException(" target array needs to be provided with the same length as the data" );
		} 	
		
		// check if values only 1 and zero
		HashSet<Double> has= new HashSet<Double> ();
		for (int i=0; i < target.length; i++){
			has.add(target[i]);
		}
		if (has.size()<=1){
			throw new IllegalStateException(" target array needs to have more 2 or more classes" );	
		}
		double uniquevalues[]= new double[has.size()];
		
		int k=0;
	    for (Iterator<Double> it = has.iterator(); it.hasNext(); ) {
	    	uniquevalues[k]= it.next();
	    	k++;
	    	}
	    // sort values
	    Arrays.sort(uniquevalues);
	    
	    classes= new String[uniquevalues.length];
	    StringIntMap4a mapper = new StringIntMap4a(classes.length,0.5F);
	    int index=0;
	    for (int j=0; j < uniquevalues.length; j++){
	    	classes[j]=uniquevalues[j]+"";
	    	mapper.put(classes[j], index);
	    	index++;
	    }
	    fstarget=new int[target.length];
	    for (int i=0; i < fstarget.length; i++){
	    	fstarget[i]=mapper.get(target[i] + "");
	    }
		
		
		if (weights==null) {
			
			weights=new double [data.length];
			for (int i=0; i < weights.length; i++){
				weights[i]=1.0;
			}
			
		} else {
			if (weights.length!=data.length){
				throw new DimensionMismatchException(weights.length,data.length);
			}
			weights=manipulate.transforms.transforms.scaleweight(weights);
			for (int i=0; i < weights.length; i++){
				weights[i]*= weights.length;
			}
		}

		// Initialise randomiser
		
		this.random = new XorShift128PlusRandom(this.seed);

		this.n_classes=classes.length;	
		
		
		if (!this.metric.equals("auc") && this.n_classes!=2){
			String last_case []=parameters[parameters.length-1];
			for (int d=0; d <last_case.length;d++){
				String splits[]=last_case[d].split(" " + "+");	
				String str_estimator=splits[0];
				boolean has_regressor_in_last_layer=false;
				if (str_estimator.contains("AdaboostForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("DecisionTreeRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("RandomForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("Vanilla2hnnregressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("multinnregressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LSVR")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LinearRegression")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LibFmRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("knnRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("KernelmodelRegressor")) {
					has_regressor_in_last_layer=true;
				} 
				
				if (has_regressor_in_last_layer){
					throw new IllegalStateException("The last layer of StackNet cannot have a regressor unless the metric is auc and it is a binary problem" );
				}
			}
		}
		

		fsmatrix trainstacker=null;
		tree_body= new estimator[parameters.length][];
		column_counts = new int[parameters.length];

		for(int level=0; level<parameters.length; level++){
			
			// change the data 
			if (level>0){
				if (this.stackdata){
					
					double temp[][] = new double [data.length][data[0].length+trainstacker.GetColumnDimension()];
					int ccc=0;
					for (int i=0; i <data.length; i++ ){ 
						ccc=0;
						for (int j=0; j <data[0].length; j++ ){
							temp[i][ccc]=data[i][j];
							ccc++;
						}
						for (int j=0; j <trainstacker.GetColumnDimension(); j++ ){
							temp[i][ccc]=trainstacker.GetElement(i, j);
							ccc++;
						}
					}
					
					data=temp;	
				}
				else {
					int ccc=0;
					 data = new double [data.length][trainstacker.GetColumnDimension()];
					 for (int i=0; i <data.length; i++ ){ 
							ccc=0;
							for (int j=0; j <trainstacker.GetColumnDimension(); j++ ){
								data[i][ccc]=trainstacker.GetElement(i, j);
								ccc++;
							}
						}					
					
				}
				
				
			}
			
			String [] level_grid=parameters[level];
			estimator[] mini_batch_tree= new estimator[level_grid.length];
			
			Thread[] thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
			estimator [] estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
			int count_of_live_threads=0;


			int temp_class=estimate_classes(level_grid,  this.n_classes, level==(parameters.length-1));
			column_counts[level]=temp_class;
			if (this.verbose){
				System.out.println(" Level: " +  (level+1) + " dimensionality: " + temp_class);
				System.out.println(" Starting cross validation ");
			}
			if (level<parameters.length -1){
			trainstacker=new fsmatrix(target.length, temp_class);
			int kfolder [][][]=kfold.getindices(this.target.length, this.folds);
			
			// begin cross validation
			for (int f=0; f < this.folds; f++){
				
					int train_indices[]=kfolder[f][0]; // train indices
					int test_indices[]=kfolder[f][1]; // test indices	
					//System.out.println(" start!");
					double X_train [][]= manipulate.select.rowselect.RowSelect2d(data, train_indices);
					double X_cv [][] = manipulate.select.rowselect.RowSelect2d(data, test_indices);
					double [] y_train=manipulate.select.rowselect.RowSelect(this.target, train_indices);
					double [] y_cv=manipulate.select.rowselect.RowSelect(this.target, test_indices);
					//double [] y_cv=manipulate.select.rowselect.RowSelect(this.target, test_indices);	
					int column_counter=0;
					
					thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
					estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
					
					
					for (int es=0; es <level_grid.length; es++ ){
						String splits[]=level_grid[es].split(" " + "+");	
						String str_estimator=splits[0];
						
						if (str_estimator.contains("AdaboostForestRegressor")) {
							mini_batch_tree[es]= new AdaboostForestRegressor(X_train);
						} else if (str_estimator.contains("AdaboostRandomForestClassifier")) {
							mini_batch_tree[es]= new AdaboostRandomForestClassifier(X_train);
						}else if (str_estimator.contains("DecisionTreeClassifier")) {
							mini_batch_tree[es]= new DecisionTreeClassifier(X_train);
						}else if (str_estimator.contains("DecisionTreeRegressor")) {
							mini_batch_tree[es]= new DecisionTreeRegressor(X_train);
						}else if (str_estimator.contains("GradientBoostingForestClassifier")) {
							mini_batch_tree[es]= new GradientBoostingForestClassifier(X_train);
						}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
							mini_batch_tree[es]= new GradientBoostingForestRegressor(X_train);
						}else if (str_estimator.contains("RandomForestClassifier")) {
							mini_batch_tree[es]= new RandomForestClassifier(X_train);
						}else if (str_estimator.contains("RandomForestRegressor")) {
							mini_batch_tree[es]= new RandomForestRegressor(X_train);
						}else if (str_estimator.contains("Vanilla2hnnregressor")) {
							mini_batch_tree[es]= new Vanilla2hnnregressor(X_train);
						}else if (str_estimator.contains("Vanilla2hnnclassifier")) {
							mini_batch_tree[es]= new Vanilla2hnnclassifier(X_train);
						}else if (str_estimator.contains("softmaxnnclassifier")) {
							mini_batch_tree[es]= new softmaxnnclassifier(X_train);
						}else if (str_estimator.contains("multinnregressor")) {
							mini_batch_tree[es]= new multinnregressor(X_train);
						}else if (str_estimator.contains("NaiveBayesClassifier")) {
							mini_batch_tree[es]= new NaiveBayesClassifier(X_train);
						}else if (str_estimator.contains("LSVR")) {
							mini_batch_tree[es]= new LSVR(X_train);
						}else if (str_estimator.contains("LSVC")) {
							mini_batch_tree[es]= new LSVC(X_train);
						}else if (str_estimator.contains("LogisticRegression")) {
							mini_batch_tree[es]= new LogisticRegression(X_train);
						}else if (str_estimator.contains("LinearRegression")) {
							mini_batch_tree[es]= new LinearRegression(X_train);
						}else if (str_estimator.contains("LibFmRegressor")) {
							mini_batch_tree[es]= new LibFmRegressor(X_train);
						}else if (str_estimator.contains("LibFmClassifier")) {
							mini_batch_tree[es]= new LibFmClassifier(X_train);
						}else if (str_estimator.contains("knnClassifier")) {
							mini_batch_tree[es]= new knnClassifier(X_train);
						}else if (str_estimator.contains("knnRegressor")) {
							mini_batch_tree[es]= new knnRegressor(X_train);
						}else if (str_estimator.contains("KernelmodelClassifier")) {
							mini_batch_tree[es]= new KernelmodelClassifier(X_train);
						}else if (str_estimator.contains("KernelmodelRegressor")) {
							mini_batch_tree[es]= new KernelmodelRegressor(X_train);
						} else {
							throw new IllegalStateException(" The selected model '" + str_estimator + "' inside the '" + level_grid[es] + "' is not recognizable!" );
						}
						mini_batch_tree[es].set_params(level_grid[es]);
						mini_batch_tree[es].set_target(y_train);
		
						estimators[count_of_live_threads]=mini_batch_tree[es];
						thread_array[count_of_live_threads]= new Thread(mini_batch_tree[es]);
						thread_array[count_of_live_threads].start();
						count_of_live_threads++;
						if (this.verbose==true){
							System.out.println("fitting model : " + (es+1));
							
						}
						
						if (count_of_live_threads==thread_array.length || es==level_grid.length-1){
							for (int s=0; s <count_of_live_threads;s++ ){
								try {
									thread_array[s].join();
								} catch (InterruptedException e) {
								   System.out.println(e.getMessage());
								   throw new IllegalStateException(" algorithm was terminated due to multithreading error");
								}
							}
							
							
							for (int s=0; s <count_of_live_threads;s++ ){
								double predictions[][]=estimators[s].predict_proba(X_cv);
								boolean is_regerssion=estimators[s].IsRegressor();
								if (predictions[0].length==2){
									predictions=manipulate.select.columnselect.ColumnSelect(predictions, new int [] {1});
								}
								
								if (this.verbose){
									if(this.n_classes==2 && this.metric.equals("auc")){
											double pr [] = manipulate.conversions.dimension.Convert(predictions);
											crossvalidation.metrics.Metric ms =new auc();
											double auc=ms.GetValue(pr,y_cv ); // the auc for the current fold	
											System.out.println(" AUC: " + auc);
										} else if ( this.metric.equals("logloss")){
											if (is_regerssion){
												double rms=rmse(predictions,y_cv);
												System.out.println(" rmse : " + rms);
											}else {
											double log=logloss (predictions,y_cv ); // the logloss for the current fold	
											System.out.println(" logloss : " + log);
											}
											
										} else if (this.metric.equals("accuracy")){
											if (is_regerssion){
												double rms=rmse(predictions,y_cv);
												System.out.println(" rmse : " + rms);
											}else {
												double acc=accuracy (predictions,y_cv ); // the accuracy for the current fold	
												System.out.println(" accuracy : " + acc);
											}
										}
							}						
								
								
								for (int j=0; j <predictions[0].length; j++ ){
									for (int i=0; i <predictions.length; i++ ){
										trainstacker.SetElement(test_indices[i], column_counter, predictions[i][j]);
									}
									column_counter+=1;
								}
								
							
								
							}							
							
							System.gc();
							count_of_live_threads=0;
							thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
							estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
						}
						
						
		
					}
					if (this.verbose==true){
						System.out.println("Done with fold: " + (f+1) + "/" + this.folds);
						
					}
				
			}
			
			}
			// we print file
			if (this.print){
				
				if (this.verbose){
					
					System.out.println("Printing reusable train for level: " + (level+1) + " as : " + this.output_name +  (level+1)+ ".csv" );
				}
				trainstacker.ToFile(this.output_name +  (level+1)+ ".csv");
				
			}
			if (this.verbose){
				System.out.println(" Level: " +  (level+1)+ " start output modelling ");
			}
			
			thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
			estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
			mini_batch_tree= new estimator[level_grid.length];
			count_of_live_threads=0;
			/* Final modelling */
			
			for (int es=0; es <level_grid.length; es++ ){
				String splits[]=level_grid[es].split(" " + "+");	
				String str_estimator=splits[0];
				
				if (str_estimator.contains("AdaboostForestRegressor")) {
					mini_batch_tree[es]= new AdaboostForestRegressor(data);
				} else if (str_estimator.contains("AdaboostRandomForestClassifier")) {
					mini_batch_tree[es]= new AdaboostRandomForestClassifier(data);
				}else if (str_estimator.contains("DecisionTreeClassifier")) {
					mini_batch_tree[es]= new DecisionTreeClassifier(data);
				}else if (str_estimator.contains("DecisionTreeRegressor")) {
					mini_batch_tree[es]= new DecisionTreeRegressor(data);
				}else if (str_estimator.contains("GradientBoostingForestClassifier")) {
					mini_batch_tree[es]= new GradientBoostingForestClassifier(data);
				}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
					mini_batch_tree[es]= new GradientBoostingForestRegressor(data);
				}else if (str_estimator.contains("RandomForestClassifier")) {
					mini_batch_tree[es]= new RandomForestClassifier(data);
				}else if (str_estimator.contains("RandomForestRegressor")) {
					mini_batch_tree[es]= new RandomForestRegressor(data);
				}else if (str_estimator.contains("Vanilla2hnnregressor")) {
					mini_batch_tree[es]= new Vanilla2hnnregressor(data);
				}else if (str_estimator.contains("Vanilla2hnnclassifier")) {
					mini_batch_tree[es]= new Vanilla2hnnclassifier(data);
				}else if (str_estimator.contains("softmaxnnclassifier")) {
					mini_batch_tree[es]= new softmaxnnclassifier(data);
				}else if (str_estimator.contains("multinnregressor")) {
					mini_batch_tree[es]= new multinnregressor(data);
				}else if (str_estimator.contains("NaiveBayesClassifier")) {
					mini_batch_tree[es]= new NaiveBayesClassifier(data);
				}else if (str_estimator.contains("LSVR")) {
					mini_batch_tree[es]= new LSVR(data);
				}else if (str_estimator.contains("LSVC")) {
					mini_batch_tree[es]= new LSVC(data);
				}else if (str_estimator.contains("LogisticRegression")) {
					mini_batch_tree[es]= new LogisticRegression(data);
				}else if (str_estimator.contains("LinearRegression")) {
					mini_batch_tree[es]= new LinearRegression(data);
				}else if (str_estimator.contains("LibFmRegressor")) {
					mini_batch_tree[es]= new LibFmRegressor(data);
				}else if (str_estimator.contains("LibFmClassifier")) {
					mini_batch_tree[es]= new LibFmClassifier(data);
				}else if (str_estimator.contains("knnClassifier")) {
					mini_batch_tree[es]= new knnClassifier(data);
				}else if (str_estimator.contains("knnRegressor")) {
					mini_batch_tree[es]= new knnRegressor(data);
				}else if (str_estimator.contains("KernelmodelClassifier")) {
					mini_batch_tree[es]= new KernelmodelClassifier(data);
				}else if (str_estimator.contains("KernelmodelRegressor")) {
					mini_batch_tree[es]= new KernelmodelRegressor(data);
				} else {
					throw new IllegalStateException(" The selected model '" + str_estimator + "' inside the '" + level_grid[es] + "' is not recognizable!" );
				}
				mini_batch_tree[es].set_params(level_grid[es]);
				mini_batch_tree[es].set_target(this.target);

				estimators[count_of_live_threads]=mini_batch_tree[es];
				thread_array[count_of_live_threads]= new Thread(mini_batch_tree[es]);
				thread_array[count_of_live_threads].start();
				count_of_live_threads++;
				if (this.verbose==true){
					System.out.println("Fitting model: " + (es+1));
					
				}
				
				if (count_of_live_threads==thread_array.length || es==level_grid.length-1){
					for (int s=0; s <count_of_live_threads;s++ ){
						try {
							thread_array[s].join();
						} catch (InterruptedException e) {
						   System.out.println(e.getMessage());
						   throw new IllegalStateException(" algorithm was terminated due to multithreading error");
						}
					}

					System.gc();
					count_of_live_threads=0;
					thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
					estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
				}
				
			}			
			
			if (this.verbose==true){
				System.out.println("Completed level: " + (level+1) + " out of " + parameters.length);
				
			}
			
			// assign trained models in the main body
			
			this.tree_body[level]=mini_batch_tree;
			
			
		}
		
	
		System.gc();
		
	}
	@Override
	public void fit(fsmatrix data) {
		// make sensible checks
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" Main data object is null or has too few cases" );
		}
		fsdataset=data;
		columndimension=data.GetColumnDimension();
		
		if (this.parameters.length<1 || (this.parameters[0].length<1) ){
			throw new IllegalStateException(" Parameters need to be provided in string format as model_name parameter_m:value_n ... " );
		}	
		if (parameters.length<2 && parameters[0].length==1){
			throw new IllegalStateException("StackNet cannot have only 1 model" );
		}	
		
		if ( !metric.equals("logloss")  && !metric.equals("accuracy") && !metric.equals("auc")){
			throw new IllegalStateException(" The metric to validate on needs to be one of logloss, accuracy or auc (for binary only) " );	
		}
		
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}	
		// make sensible checks on the target data
		if ( (target==null || target.length!=data.GetRowDimension())  ){
			throw new IllegalStateException(" target array needs to be provided with the same length as the data" );
		} 	
		
		// check if values only 1 and zero
		HashSet<Double> has= new HashSet<Double> ();
		for (int i=0; i < target.length; i++){
			has.add(target[i]);
		}
		if (has.size()<=1){
			throw new IllegalStateException(" target array needs to have more 2 or more classes" );	
		}
		double uniquevalues[]= new double[has.size()];
		
		int k=0;
	    for (Iterator<Double> it = has.iterator(); it.hasNext(); ) {
	    	uniquevalues[k]= it.next();
	    	k++;
	    	}
	    // sort values
	    Arrays.sort(uniquevalues);
	    
	    classes= new String[uniquevalues.length];
	    StringIntMap4a mapper = new StringIntMap4a(classes.length,0.5F);
	    int index=0;
	    for (int j=0; j < uniquevalues.length; j++){
	    	classes[j]=uniquevalues[j]+"";
	    	mapper.put(classes[j], index);
	    	index++;
	    }
	    fstarget=new int[target.length];
	    for (int i=0; i < fstarget.length; i++){
	    	fstarget[i]=mapper.get(target[i] + "");
	    }		
		if (weights==null) {
			
			weights=new double [data.GetRowDimension()];
			for (int i=0; i < weights.length; i++){
				weights[i]=1.0;
			}
			
		} else {
			if (weights.length!=data.GetRowDimension()){
				throw new DimensionMismatchException(weights.length,data.GetRowDimension());
			}
			weights=manipulate.transforms.transforms.scaleweight(weights);
			for (int i=0; i < weights.length; i++){
				weights[i]*= weights.length;
			}
		}

		// Initialise randomiser
		
		this.random = new XorShift128PlusRandom(this.seed);

		this.n_classes=classes.length;			

		if (!this.metric.equals("auc") && this.n_classes!=2){
			String last_case []=parameters[parameters.length-1];
			for (int d=0; d <last_case.length;d++){
				String splits[]=last_case[d].split(" " + "+");	
				String str_estimator=splits[0];
				boolean has_regressor_in_last_layer=false;
				if (str_estimator.contains("AdaboostForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("DecisionTreeRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("RandomForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("Vanilla2hnnregressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("multinnregressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LSVR")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LinearRegression")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LibFmRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("knnRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("KernelmodelRegressor")) {
					has_regressor_in_last_layer=true;
				} 
				
				if (has_regressor_in_last_layer){
					throw new IllegalStateException("The last layer of StackNet cannot have a regressor unless the metric is auc and it is a binary problem" );
				}
			}
		}		
		
		fsmatrix trainstacker=null;
		tree_body= new estimator[parameters.length][];
		column_counts = new int[parameters.length];

		for(int level=0; level<parameters.length; level++){
			
			// change the data 
			if (level>0){
				if (this.stackdata){
					
					double temp[][] = new double [data.GetRowDimension()][data.GetColumnDimension()+trainstacker.GetColumnDimension()];
					int ccc=0;
					for (int i=0; i <data.GetRowDimension(); i++ ){ 
						ccc=0;
						for (int j=0; j <data.GetColumnDimension(); j++ ){
							temp[i][ccc]=data.GetElement(i, j);
							ccc++;
						}
						for (int j=0; j <trainstacker.GetColumnDimension(); j++ ){
							temp[i][ccc]=trainstacker.GetElement(i, j);
							ccc++;
						}
					}
					
					data=new fsmatrix(temp);	
				}
				else {
					int ccc=0;
					 data =new fsmatrix(data.GetRowDimension(),trainstacker.GetColumnDimension());
					 for (int i=0; i <data.GetRowDimension(); i++ ){ 
							ccc=0;
							for (int j=0; j <trainstacker.GetColumnDimension(); j++ ){
								data.SetElement(i, ccc, trainstacker.GetElement(i, j));
								ccc++;
							}
						}					
					
				}
				
				
			}
			
			String [] level_grid=parameters[level];
			estimator[] mini_batch_tree= new estimator[level_grid.length];
			
			Thread[] thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
			estimator [] estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
			int count_of_live_threads=0;


			int temp_class=estimate_classes(level_grid,  this.n_classes, level==(parameters.length-1));
			column_counts[level] = temp_class;
			if (this.verbose){
				System.out.println(" Level: " +  (level+1) + " dimensionality: " + temp_class);
				System.out.println(" Starting cross validation ");
			}
			if (level<parameters.length -1){
			trainstacker=new fsmatrix(target.length, temp_class);
			int kfolder [][][]=kfold.getindices(this.target.length, this.folds);
			
			// begin cross validation
			for (int f=0; f < this.folds; f++){
				
					int train_indices[]=kfolder[f][0]; // train indices
					int test_indices[]=kfolder[f][1]; // test indices	
					//System.out.println(" start!");
					fsmatrix X_train = data.makerowsubset(train_indices);
					fsmatrix X_cv  =data.makerowsubset(test_indices);
					double [] y_train=manipulate.select.rowselect.RowSelect(this.target, train_indices);
					double [] y_cv=manipulate.select.rowselect.RowSelect(this.target, test_indices);	
					int column_counter=0;
					
					thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
					estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
					
					
					for (int es=0; es <level_grid.length; es++ ){
						String splits[]=level_grid[es].split(" " + "+");	
						String str_estimator=splits[0];
						
						if (str_estimator.contains("AdaboostForestRegressor")) {
							mini_batch_tree[es]= new AdaboostForestRegressor(X_train);
						} else if (str_estimator.contains("AdaboostRandomForestClassifier")) {
							mini_batch_tree[es]= new AdaboostRandomForestClassifier(X_train);
						}else if (str_estimator.contains("DecisionTreeClassifier")) {
							mini_batch_tree[es]= new DecisionTreeClassifier(X_train);
						}else if (str_estimator.contains("DecisionTreeRegressor")) {
							mini_batch_tree[es]= new DecisionTreeRegressor(X_train);
						}else if (str_estimator.contains("GradientBoostingForestClassifier")) {
							mini_batch_tree[es]= new GradientBoostingForestClassifier(X_train);
						}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
							mini_batch_tree[es]= new GradientBoostingForestRegressor(X_train);
						}else if (str_estimator.contains("RandomForestClassifier")) {
							mini_batch_tree[es]= new RandomForestClassifier(X_train);
						}else if (str_estimator.contains("RandomForestRegressor")) {
							mini_batch_tree[es]= new RandomForestRegressor(X_train);
						}else if (str_estimator.contains("Vanilla2hnnregressor")) {
							mini_batch_tree[es]= new Vanilla2hnnregressor(X_train);
						}else if (str_estimator.contains("Vanilla2hnnclassifier")) {
							mini_batch_tree[es]= new Vanilla2hnnclassifier(X_train);
						}else if (str_estimator.contains("softmaxnnclassifier")) {
							mini_batch_tree[es]= new softmaxnnclassifier(X_train);
						}else if (str_estimator.contains("multinnregressor")) {
							mini_batch_tree[es]= new multinnregressor(X_train);
						}else if (str_estimator.contains("NaiveBayesClassifier")) {
							mini_batch_tree[es]= new NaiveBayesClassifier(X_train);
						}else if (str_estimator.contains("LSVR")) {
							mini_batch_tree[es]= new LSVR(X_train);
						}else if (str_estimator.contains("LSVC")) {
							mini_batch_tree[es]= new LSVC(X_train);
						}else if (str_estimator.contains("LogisticRegression")) {
							mini_batch_tree[es]= new LogisticRegression(X_train);
						}else if (str_estimator.contains("LinearRegression")) {
							mini_batch_tree[es]= new LinearRegression(X_train);
						}else if (str_estimator.contains("LibFmRegressor")) {
							mini_batch_tree[es]= new LibFmRegressor(X_train);
						}else if (str_estimator.contains("LibFmClassifier")) {
							mini_batch_tree[es]= new LibFmClassifier(X_train);
						}else if (str_estimator.contains("knnClassifier")) {
							mini_batch_tree[es]= new knnClassifier(X_train);
						}else if (str_estimator.contains("knnRegressor")) {
							mini_batch_tree[es]= new knnRegressor(X_train);
						}else if (str_estimator.contains("KernelmodelClassifier")) {
							mini_batch_tree[es]= new KernelmodelClassifier(X_train);
						}else if (str_estimator.contains("KernelmodelRegressor")) {
							mini_batch_tree[es]= new KernelmodelRegressor(X_train);
						} else {
							throw new IllegalStateException(" The selected model '" + str_estimator + "' inside the '" + level_grid[es] + "' is not recognizable!" );
						}
						mini_batch_tree[es].set_params(level_grid[es]);
						mini_batch_tree[es].set_target(y_train);
		
						estimators[count_of_live_threads]=mini_batch_tree[es];
						thread_array[count_of_live_threads]= new Thread(mini_batch_tree[es]);
						thread_array[count_of_live_threads].start();
						count_of_live_threads++;
						if (this.verbose==true){
							System.out.println("Fitting model: " + (es+1));
							
						}
						
						if (count_of_live_threads==thread_array.length || es==level_grid.length-1){
							for (int s=0; s <count_of_live_threads;s++ ){
								try {
									thread_array[s].join();
								} catch (InterruptedException e) {
								   System.out.println(e.getMessage());
								   throw new IllegalStateException(" algorithm was terminated due to multithreading error");
								}
							}
							
							
							for (int s=0; s <count_of_live_threads;s++ ){
								double predictions[][]=estimators[s].predict_proba(X_cv);
								boolean is_regerssion=estimators[s].IsRegressor();
								if (predictions[0].length==2){
									predictions=manipulate.select.columnselect.ColumnSelect(predictions, new int [] {1});

								}
								// metrics' calculation
								if (this.verbose){
									if(this.n_classes==2 && this.metric.equals("auc")){
											double pr [] = manipulate.conversions.dimension.Convert(predictions);
											crossvalidation.metrics.Metric ms =new auc();
											double auc=ms.GetValue(pr,y_cv ); // the auc for the current fold	
											System.out.println(" AUC: " + auc);
										} else if ( this.metric.equals("logloss")){
											if (is_regerssion){
												double rms=rmse(predictions,y_cv);
												System.out.println(" rmse : " + rms);
											}else {
											double log=logloss (predictions,y_cv ); // the logloss for the current fold	
											System.out.println(" logloss : " + log);
											}
											
										} else if (this.metric.equals("accuracy")){
											if (is_regerssion){
												double rms=rmse(predictions,y_cv);
												System.out.println(" rmse : " + rms);
											}else {
												double acc=accuracy (predictions,y_cv ); // the accuracy for the current fold	
												System.out.println(" accuracy : " + acc);
											}
										}
							}
								
								
								for (int j=0; j <predictions[0].length; j++ ){
									for (int i=0; i <predictions.length; i++ ){
										trainstacker.SetElement(test_indices[i], column_counter, predictions[i][j]);
									}
									column_counter+=1;
								}
								
							
								
							}							
							
							System.gc();
							count_of_live_threads=0;
							thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
							estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
						}
						
						
				
					}
					if (this.verbose==true){
						System.out.println("Done with fold: " + (f+1) + "/" + this.folds);
						
					}
				
			}

			}
			if (this.print){
				
				if (this.verbose){
					
					System.out.println("Printing reusable train for level: " + (level+1) + " as : " + this.output_name +  (level+1)+ ".csv" );
				}
				trainstacker.ToFile(this.output_name +  (level+1)+ ".csv");
				
			}
			
			if (this.verbose){
				System.out.println(" Level: " +  (level+1)+ " start output modelling ");
			}
			
			thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
			estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
			mini_batch_tree= new estimator[level_grid.length];
			count_of_live_threads=0;
			/* Final modelling */
			
			for (int es=0; es <level_grid.length; es++ ){
				String splits[]=level_grid[es].split(" " + "+");	
				String str_estimator=splits[0];
				
				if (str_estimator.contains("AdaboostForestRegressor")) {
					mini_batch_tree[es]= new AdaboostForestRegressor(data);
				} else if (str_estimator.contains("AdaboostRandomForestClassifier")) {
					mini_batch_tree[es]= new AdaboostRandomForestClassifier(data);
				}else if (str_estimator.contains("DecisionTreeClassifier")) {
					mini_batch_tree[es]= new DecisionTreeClassifier(data);
				}else if (str_estimator.contains("DecisionTreeRegressor")) {
					mini_batch_tree[es]= new DecisionTreeRegressor(data);
				}else if (str_estimator.contains("GradientBoostingForestClassifier")) {
					mini_batch_tree[es]= new GradientBoostingForestClassifier(data);
				}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
					mini_batch_tree[es]= new GradientBoostingForestRegressor(data);
				}else if (str_estimator.contains("RandomForestClassifier")) {
					mini_batch_tree[es]= new RandomForestClassifier(data);
				}else if (str_estimator.contains("RandomForestRegressor")) {
					mini_batch_tree[es]= new RandomForestRegressor(data);
				}else if (str_estimator.contains("Vanilla2hnnregressor")) {
					mini_batch_tree[es]= new Vanilla2hnnregressor(data);
				}else if (str_estimator.contains("Vanilla2hnnclassifier")) {
					mini_batch_tree[es]= new Vanilla2hnnclassifier(data);
				}else if (str_estimator.contains("softmaxnnclassifier")) {
					mini_batch_tree[es]= new softmaxnnclassifier(data);
				}else if (str_estimator.contains("multinnregressor")) {
					mini_batch_tree[es]= new multinnregressor(data);
				}else if (str_estimator.contains("NaiveBayesClassifier")) {
					mini_batch_tree[es]= new NaiveBayesClassifier(data);
				}else if (str_estimator.contains("LSVR")) {
					mini_batch_tree[es]= new LSVR(data);
				}else if (str_estimator.contains("LSVC")) {
					mini_batch_tree[es]= new LSVC(data);
				}else if (str_estimator.contains("LogisticRegression")) {
					mini_batch_tree[es]= new LogisticRegression(data);
				}else if (str_estimator.contains("LinearRegression")) {
					mini_batch_tree[es]= new LinearRegression(data);
				}else if (str_estimator.contains("LibFmRegressor")) {
					mini_batch_tree[es]= new LibFmRegressor(data);
				}else if (str_estimator.contains("LibFmClassifier")) {
					mini_batch_tree[es]= new LibFmClassifier(data);
				}else if (str_estimator.contains("knnClassifier")) {
					mini_batch_tree[es]= new knnClassifier(data);
				}else if (str_estimator.contains("knnRegressor")) {
					mini_batch_tree[es]= new knnRegressor(data);
				}else if (str_estimator.contains("KernelmodelClassifier")) {
					mini_batch_tree[es]= new KernelmodelClassifier(data);
				}else if (str_estimator.contains("KernelmodelRegressor")) {
					mini_batch_tree[es]= new KernelmodelRegressor(data);
				} else {
					throw new IllegalStateException(" The selected model '" + str_estimator + "' inside the '" + level_grid[es] + "' is not recognizable!" );
				}
				mini_batch_tree[es].set_params(level_grid[es]);
				mini_batch_tree[es].set_target(this.target);

				estimators[count_of_live_threads]=mini_batch_tree[es];
				thread_array[count_of_live_threads]= new Thread(mini_batch_tree[es]);
				thread_array[count_of_live_threads].start();
				count_of_live_threads++;
				if (this.verbose==true){
					System.out.println("Fitting model : " + (es+1));
					
				}
				
				if (count_of_live_threads==thread_array.length || es==level_grid.length-1){
					for (int s=0; s <count_of_live_threads;s++ ){
						try {
							thread_array[s].join();
						} catch (InterruptedException e) {
						   System.out.println(e.getMessage());
						   throw new IllegalStateException(" algorithm was terminated due to multithreading error");
						}
					}

					System.gc();
					count_of_live_threads=0;
					thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
					estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
				}
				
			}			
			
			if (this.verbose==true){
				System.out.println("Completed level: " + (level+1) + " out of " + parameters.length);
				
			}
			
			// assign trained models in the main body
			
			this.tree_body[level]=mini_batch_tree;
			
			
		}
		
	
		System.gc();


		
	}
	
	@Override
	public void fit(smatrix data) {
		// make sensible checks
		if (data==null || data.GetRowDimension()<=0){
			throw new IllegalStateException(" Main data object is null or has too few cases" );
		}
		sdataset=data;
		columndimension=data.GetColumnDimension();
		if (this.parameters.length<1 || (this.parameters[0].length<1) ){
			throw new IllegalStateException(" Parameters need to be provided in string format as model_name parameter_m:value_n ... " );
		}	
		if (parameters.length<2 && parameters[0].length==1){
			throw new IllegalStateException("StackNet cannot have only 1 model" );
		}
		
		if ( !metric.equals("logloss")  && !metric.equals("accuracy") && !metric.equals("auc")){
			throw new IllegalStateException(" The metric to validate on needs to be one of logloss, accuracy or auc (for binary only) " );	
		}
		if (this.threads<=0){
			this.threads=Runtime.getRuntime().availableProcessors();
			if (this.threads<1){
				this.threads=1;
			}
		}	
		// make sensible checks on the target data
		if ( (target==null || target.length!=data.GetRowDimension())  ){
			throw new IllegalStateException(" target array needs to be provided with the same length as the data" );
		} 	
		
		// check if values only 1 and zero
		HashSet<Double> has= new HashSet<Double> ();
		for (int i=0; i < target.length; i++){
			has.add(target[i]);
		}
		if (has.size()<=1){
			throw new IllegalStateException(" target array needs to have more 2 or more classes" );	
		}
		double uniquevalues[]= new double[has.size()];
		
		int k=0;
	    for (Iterator<Double> it = has.iterator(); it.hasNext(); ) {
	    	uniquevalues[k]= it.next();
	    	k++;
	    	}
	    // sort values
	    Arrays.sort(uniquevalues);
	    
	    classes= new String[uniquevalues.length];
	    StringIntMap4a mapper = new StringIntMap4a(classes.length,0.5F);
	    int index=0;
	    for (int j=0; j < uniquevalues.length; j++){
	    	classes[j]=uniquevalues[j]+"";
	    	mapper.put(classes[j], index);
	    	index++;
	    }
	    fstarget=new int[target.length];
	    for (int i=0; i < fstarget.length; i++){
	    	fstarget[i]=mapper.get(target[i] + "");
	    }
	    
		if (weights==null) {
			
			weights=new double [data.GetRowDimension()];
			for (int i=0; i < weights.length; i++){
				weights[i]=1.0;
			}
			
		} else {
			if (weights.length!=data.GetRowDimension()){
				throw new DimensionMismatchException(weights.length,data.GetRowDimension());
			}
			weights=manipulate.transforms.transforms.scaleweight(weights);
			for (int i=0; i < weights.length; i++){
				weights[i]*= weights.length;
			}
		}

		// Initialise randomiser
		
		this.random = new XorShift128PlusRandom(this.seed);

		this.n_classes=classes.length;			

		if (!this.metric.equals("auc") && this.n_classes!=2){
			String last_case []=parameters[parameters.length-1];
			for (int d=0; d <last_case.length;d++){
				String splits[]=last_case[d].split(" " + "+");	
				String str_estimator=splits[0];
				boolean has_regressor_in_last_layer=false;
				if (str_estimator.contains("AdaboostForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("DecisionTreeRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("RandomForestRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("Vanilla2hnnregressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("multinnregressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LSVR")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LinearRegression")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("LibFmRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("knnRegressor")) {
					has_regressor_in_last_layer=true;
				}else if (str_estimator.contains("KernelmodelRegressor")) {
					has_regressor_in_last_layer=true;
				} 
				
				if (has_regressor_in_last_layer){
					throw new IllegalStateException("The last layer of StackNet cannot have a regressor unless the metric is auc and it is a binary problem" );
				}
			}
		}		
		
		
		fsmatrix trainstacker=null;
		tree_body= new estimator[parameters.length][];
		column_counts = new int[parameters.length];

		for(int level=0; level<parameters.length; level++){
			
			// change the data 
			if (level>0){
				/*
				if (this.stackdata){
					
					double temp[][] = new double [data.GetRowDimension()][data.GetColumnDimension()+trainstacker.GetColumnDimension()];
					int ccc=0;
					for (int i=0; i <data.GetRowDimension(); i++ ){ 
						ccc=0;
						for (int j=0; j <data.GetColumnDimension(); j++ ){
							temp[i][ccc]=data.GetElement(i, j);
							ccc++;
						}
						for (int j=0; j <trainstacker.GetColumnDimension(); j++ ){
							temp[i][ccc]=trainstacker.GetElement(i, j);
							ccc++;
						}
					}
					
					data=new smatrix(temp);	
				}
				else {*/
					 data =new smatrix(trainstacker);
					
					
				//}
				
				
			}
			
			String [] level_grid=parameters[level];
			estimator[] mini_batch_tree= new estimator[level_grid.length];
			
			Thread[] thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
			estimator [] estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
			int count_of_live_threads=0;
			
			int temp_class=estimate_classes(level_grid,  this.n_classes, level==(parameters.length-1));
			column_counts[level] = temp_class;
			
			if (this.verbose){
				System.out.println(" Level: " +  (level+1) + " dimensionality: " + temp_class);
				System.out.println(" Starting cross validation ");
			}
			if (level<parameters.length -1){
			trainstacker=new fsmatrix(target.length, temp_class);
			int kfolder [][][]=kfold.getindices(this.target.length, this.folds);
			
			// begin cross validation
			for (int f=0; f < this.folds; f++){
				
					int train_indices[]=kfolder[f][0]; // train indices
					int test_indices[]=kfolder[f][1]; // test indices	
					//System.out.println(" start!");
					smatrix X_train = data.makesubmatrix(train_indices);
					smatrix X_cv  =data.makesubmatrix(test_indices);
					double [] y_train=manipulate.select.rowselect.RowSelect(this.target, train_indices);
					double [] y_cv=manipulate.select.rowselect.RowSelect(this.target, test_indices);	
					int column_counter=0;
					
					thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
					estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
					
					
					for (int es=0; es <level_grid.length; es++ ){
						String splits[]=level_grid[es].split(" " + "+");	
						String str_estimator=splits[0];
						
						if (str_estimator.contains("AdaboostForestRegressor")) {
							mini_batch_tree[es]= new AdaboostForestRegressor(X_train);
						} else if (str_estimator.contains("AdaboostRandomForestClassifier")) {
							mini_batch_tree[es]= new AdaboostRandomForestClassifier(X_train);
						}else if (str_estimator.contains("DecisionTreeClassifier")) {
							mini_batch_tree[es]= new DecisionTreeClassifier(X_train);
						}else if (str_estimator.contains("DecisionTreeRegressor")) {
							mini_batch_tree[es]= new DecisionTreeRegressor(X_train);
						}else if (str_estimator.contains("GradientBoostingForestClassifier")) {
							mini_batch_tree[es]= new GradientBoostingForestClassifier(X_train);
						}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
							mini_batch_tree[es]= new GradientBoostingForestRegressor(X_train);
						}else if (str_estimator.contains("RandomForestClassifier")) {
							mini_batch_tree[es]= new RandomForestClassifier(X_train);
						}else if (str_estimator.contains("RandomForestRegressor")) {
							mini_batch_tree[es]= new RandomForestRegressor(X_train);
						}else if (str_estimator.contains("Vanilla2hnnregressor")) {
							mini_batch_tree[es]= new Vanilla2hnnregressor(X_train);
						}else if (str_estimator.contains("Vanilla2hnnclassifier")) {
							mini_batch_tree[es]= new Vanilla2hnnclassifier(X_train);
						}else if (str_estimator.contains("softmaxnnclassifier")) {
							mini_batch_tree[es]= new softmaxnnclassifier(X_train);
						}else if (str_estimator.contains("multinnregressor")) {
							mini_batch_tree[es]= new multinnregressor(X_train);
						}else if (str_estimator.contains("NaiveBayesClassifier")) {
							mini_batch_tree[es]= new NaiveBayesClassifier(X_train);
						}else if (str_estimator.contains("LSVR")) {
							mini_batch_tree[es]= new LSVR(X_train);
						}else if (str_estimator.contains("LSVC")) {
							mini_batch_tree[es]= new LSVC(X_train);
						}else if (str_estimator.contains("LogisticRegression")) {
							mini_batch_tree[es]= new LogisticRegression(X_train);
						}else if (str_estimator.contains("LinearRegression")) {
							mini_batch_tree[es]= new LinearRegression(X_train);
						}else if (str_estimator.contains("LibFmRegressor")) {
							mini_batch_tree[es]= new LibFmRegressor(X_train);
						}else if (str_estimator.contains("LibFmClassifier")) {
							mini_batch_tree[es]= new LibFmClassifier(X_train);
						}else if (str_estimator.contains("knnClassifier")) {
							mini_batch_tree[es]= new knnClassifier(X_train);
						}else if (str_estimator.contains("knnRegressor")) {
							mini_batch_tree[es]= new knnRegressor(X_train);
						}else if (str_estimator.contains("KernelmodelClassifier")) {
							mini_batch_tree[es]= new KernelmodelClassifier(X_train);
						}else if (str_estimator.contains("KernelmodelRegressor")) {
							mini_batch_tree[es]= new KernelmodelRegressor(X_train);
						} else {
							throw new IllegalStateException(" The selected model '" + str_estimator + "' inside the '" + level_grid[es] + "' is not recognizable!" );
						}
						mini_batch_tree[es].set_params(level_grid[es]);
						mini_batch_tree[es].set_target(y_train);
		
						estimators[count_of_live_threads]=mini_batch_tree[es];
						thread_array[count_of_live_threads]= new Thread(mini_batch_tree[es]);
						thread_array[count_of_live_threads].start();
						count_of_live_threads++;
						if (this.verbose==true){
							System.out.println("Fitting model : " + es);
							
						}
						
						if (count_of_live_threads==thread_array.length || es==level_grid.length-1){
							for (int s=0; s <count_of_live_threads;s++ ){
								try {
									thread_array[s].join();
								} catch (InterruptedException e) {
								   System.out.println(e.getMessage());
								   throw new IllegalStateException(" algorithm was terminated due to multithreading error");
								}
							}
							
							
							for (int s=0; s <count_of_live_threads;s++ ){
								double predictions[][]=estimators[s].predict_proba(X_cv);
								boolean is_regerssion=estimators[s].IsRegressor();
								if (predictions[0].length==2){
									predictions=manipulate.select.columnselect.ColumnSelect(predictions, new int [] {1});
								}
								
								if (this.verbose){
									if(this.n_classes==2 && this.metric.equals("auc")){
											double pr [] = manipulate.conversions.dimension.Convert(predictions);
											crossvalidation.metrics.Metric ms =new auc();
											double auc=ms.GetValue(pr,y_cv ); // the auc for the current fold	
											System.out.println(" AUC: " + auc);
										} else if ( this.metric.equals("logloss")){
											if (is_regerssion){
												double rms=rmse(predictions,y_cv);
												System.out.println(" rmse : " + rms);
											}else {
											double log=logloss (predictions,y_cv ); // the logloss for the current fold	
											System.out.println(" logloss : " + log);
											}
											
										} else if (this.metric.equals("accuracy")){
											if (is_regerssion){
												double rms=rmse(predictions,y_cv);
												System.out.println(" rmse : " + rms);
											}else {
												double acc=accuracy (predictions,y_cv ); // the accuracy for the current fold	
												System.out.println(" accuracy : " + acc);
											}
										}
							}
								for (int j=0; j <predictions[0].length; j++ ){
									for (int i=0; i <predictions.length; i++ ){
										trainstacker.SetElement(test_indices[i], column_counter, predictions[i][j]);
									}
									column_counter+=1;
								}
								
							
								
							}							
							
							System.gc();
							count_of_live_threads=0;
							thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
							estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
						}
						
						

					}
					
					if (this.verbose==true){
						System.out.println("Done with fold: " + (f+1) + "/" + this.folds);
						
					}
				
			}
			}
			if (this.print){
				
				if (this.verbose){
					
					System.out.println("Printing reusable train for level: " + (level+1) + " as : " + this.output_name +  (level+1)+ ".csv" );
				}
				trainstacker.ToFile(this.output_name +  (level+1)+ ".csv");
				
			}
			
			if (this.verbose){
				System.out.println(" Level: " +  (level+1)+ " start output modelling ");
			}
			
			thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
			estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
			mini_batch_tree= new estimator[level_grid.length];
			count_of_live_threads=0;
			/* Final modelling */
			
			for (int es=0; es <level_grid.length; es++ ){
				String splits[]=level_grid[es].split(" " + "+");	
				String str_estimator=splits[0];
				
				if (str_estimator.contains("AdaboostForestRegressor")) {
					mini_batch_tree[es]= new AdaboostForestRegressor(data);
				} else if (str_estimator.contains("AdaboostRandomForestClassifier")) {
					mini_batch_tree[es]= new AdaboostRandomForestClassifier(data);
				}else if (str_estimator.contains("DecisionTreeClassifier")) {
					mini_batch_tree[es]= new DecisionTreeClassifier(data);
				}else if (str_estimator.contains("DecisionTreeRegressor")) {
					mini_batch_tree[es]= new DecisionTreeRegressor(data);
				}else if (str_estimator.contains("GradientBoostingForestClassifier")) {
					mini_batch_tree[es]= new GradientBoostingForestClassifier(data);
				}else if (str_estimator.contains("GradientBoostingForestRegressor")) {
					mini_batch_tree[es]= new GradientBoostingForestRegressor(data);
				}else if (str_estimator.contains("RandomForestClassifier")) {
					mini_batch_tree[es]= new RandomForestClassifier(data);
				}else if (str_estimator.contains("RandomForestRegressor")) {
					mini_batch_tree[es]= new RandomForestRegressor(data);
				}else if (str_estimator.contains("Vanilla2hnnregressor")) {
					mini_batch_tree[es]= new Vanilla2hnnregressor(data);
				}else if (str_estimator.contains("Vanilla2hnnclassifier")) {
					mini_batch_tree[es]= new Vanilla2hnnclassifier(data);
				}else if (str_estimator.contains("softmaxnnclassifier")) {
					mini_batch_tree[es]= new softmaxnnclassifier(data);
				}else if (str_estimator.contains("multinnregressor")) {
					mini_batch_tree[es]= new multinnregressor(data);
				}else if (str_estimator.contains("NaiveBayesClassifier")) {
					mini_batch_tree[es]= new NaiveBayesClassifier(data);
				}else if (str_estimator.contains("LSVR")) {
					mini_batch_tree[es]= new LSVR(data);
				}else if (str_estimator.contains("LSVC")) {
					mini_batch_tree[es]= new LSVC(data);
				}else if (str_estimator.contains("LogisticRegression")) {
					mini_batch_tree[es]= new LogisticRegression(data);
				}else if (str_estimator.contains("LinearRegression")) {
					mini_batch_tree[es]= new LinearRegression(data);
				}else if (str_estimator.contains("LibFmRegressor")) {
					mini_batch_tree[es]= new LibFmRegressor(data);
				}else if (str_estimator.contains("LibFmClassifier")) {
					mini_batch_tree[es]= new LibFmClassifier(data);
				}else if (str_estimator.contains("knnClassifier")) {
					mini_batch_tree[es]= new knnClassifier(data);
				}else if (str_estimator.contains("knnRegressor")) {
					mini_batch_tree[es]= new knnRegressor(data);
				}else if (str_estimator.contains("KernelmodelClassifier")) {
					mini_batch_tree[es]= new KernelmodelClassifier(data);
				}else if (str_estimator.contains("KernelmodelRegressor")) {
					mini_batch_tree[es]= new KernelmodelRegressor(data);
				} else {
					throw new IllegalStateException(" The selected model '" + str_estimator + "' inside the '" + level_grid[es] + "' is not recognizable!" );
				}
				mini_batch_tree[es].set_params(level_grid[es]);
				mini_batch_tree[es].set_target(this.target);

				estimators[count_of_live_threads]=mini_batch_tree[es];
				thread_array[count_of_live_threads]= new Thread(mini_batch_tree[es]);
				thread_array[count_of_live_threads].start();
				count_of_live_threads++;
				if (this.verbose==true){
					System.out.println("Fitting model : " + es);
					
				}
				
				if (count_of_live_threads==thread_array.length || es==level_grid.length-1){
					for (int s=0; s <count_of_live_threads;s++ ){
						try {
							thread_array[s].join();
						} catch (InterruptedException e) {
						   System.out.println(e.getMessage());
						   throw new IllegalStateException(" algorithm was terminated due to multithreading error");
						}
					}

					System.gc();
					count_of_live_threads=0;
					thread_array= new Thread[(this.threads>level_grid.length)?level_grid.length: this.threads];
					estimators= new estimator[(this.threads>level_grid.length)?level_grid.length: this.threads];
				}
				
			}			
			
			if (this.verbose==true){
				System.out.println("Completed level: " + (level+1) + " out of " + parameters.length);
				
			}
			
			// assign trained models in the main body
			
			this.tree_body[level]=mini_batch_tree;
			
			
		}
		
	
		System.gc();


		
		// calculate first node
			
	}
  
	/**
	 * Retrieve the number of target variables
	 */
	public int getnumber_of_targets(){
		return n_classes;
	}
	
	
	public double get_sum(double array []){
		double a=0.0;
		for (int i=0; i <array.length; i++ ){
			a+=array[i];
		}
		return a;
	}
	
	/**
	 * 
	 * @returns the closest integer that reflects this percentage!
	 * <p> it may sound strange, random.nextint can be significantly faster than nextdouble()
	 */
	public int get_random_integer(double percentage){
		
		double per= Math.min(Math.max(0, percentage),1.0);
		double difference=2147483647.0+(2147483648.0);
		int point=(int)(-2147483648.0 +  (per*difference ));
		
		return point;
		
	}

	@Override
	public String GetType() {
		return "classifier";
	}
	@Override
	public boolean SupportsWeights() {
		return true;
	}

	@Override
	public String GetName() {
		return "StackNetClassifier";
	}

	@Override
	public void PrintInformation() {
		
		System.out.println("Classifier: StackNetClassifier");
		System.out.println("Classes: " + n_classes);
		System.out.println("Supports Weights:  True");
		System.out.println("Column dimension: " + columndimension);						
		System.out.println("threads : "+ this.threads);			
		System.out.println("Seed: "+ seed);	
		System.out.println("print at each level: "+ this.print);		
		System.out.println("output suffix: "+ this.output_name);		
		System.out.println("Verbality: "+ verbose);			
		if (this.tree_body==null){
			System.out.println("Trained: False");	
		} else {
			System.out.println("Trained: True");
		}
		
	}

	@Override
	public boolean HasTheSametype(estimator a) {
		if (a.GetType().equals(this.GetType())){
			return true;
		} else {
		return false;
		}
	}

	@Override
	public boolean isfitted() {
		if (this.tree_body!=null || tree_body.length>0){
			return true;
		} else {
		return false;
		}
	}

	@Override
	public boolean IsRegressor() {
		return false  ;
	}

	@Override
	public boolean IsClassifier() {
		return true;
	}

	@Override
	public void reset() {
		this.tree_body= null;
		n_classes=0;
		threads=1;
		this.print=false;
		this.output_name="stacknet";
		this.random=null;
		this.feature_importances.clone();
		columndimension=0;
		this.classes=null;
		seed=1;
		random=null;
		target=null;
		fstarget=null;
		target=null;
		fstarget=null;
		starget=null;
		weights=null;
		verbose=true;

		
		
	}

	@Override
	public estimator copy() {
		StackNetClassifier br = new StackNetClassifier();
		estimator[][] tree_bodys= new estimator[this.tree_body.length][];
        for (int i=0; i <tree_bodys.length; i++ ){
        	tree_bodys[i]= tree_body[i];
        }
        br.tree_body=tree_bodys;
        //br.shrinkage=this.shrinkage;
		br.n_classes=this.n_classes;
		br.threads=this.threads;
		br.columndimension=this.columndimension;
		br.seed=this.seed;
		br.print=this.print;
		br.output_name=this.output_name;
		br.random=this.random;
		br.target=manipulate.copies.copies.Copy(this.target.clone());
		br.target2d=manipulate.copies.copies.Copy(this.target2d.clone());	
		br.fstarget=(this.fstarget.clone());
		br.starget=(smatrix) this.starget.Copy();
		br.weights=manipulate.copies.copies.Copy(this.weights.clone());
		br.verbose=this.verbose;
		return br;
	}
	
	@Override	
	public void set_params(String params){

	}

	@Override
	public scaler ReturnScaler() {
		return null;
	}
	@Override
	public void setScaler(scaler sc) {

	}
	@Override
	public void setSeed(int seed) {
		this.seed=seed;}	
	
	@Override	
	public void set_target(double data []){
		if (data==null || data.length<=0){
			throw new IllegalStateException(" There is nothing to train on" );
		}
		this.target=data;
	}
	
	
	
	
	/**
	 * 
	 * @param previous_predictions : Previous predictions 
	 * @param new_predictions : New predictions to be added to the new ones
	 */
		public void append_predictions_score(double previous_predictions [][],  fsmatrix new_predictions , double shrink){
			
			if (new_predictions.GetColumnDimension()==1){		
				for (int i=0; i <previous_predictions.length; i++ ){
							previous_predictions[i][0]+= new_predictions.GetElement(i, 0)*shrink;			
					
				}
				
			}else {
			
				for (int i=0; i <previous_predictions.length; i++ ){
					for (int j=0; j <previous_predictions[0].length; j++ ){
						previous_predictions[i][j]+= new_predictions.GetElement(i, j)*shrink;

					}
					

		
				}
				
			}
		}
		/**
		 * 
		 * @param previous_predictions : Previous predictions 
		 * @param new_predictions : New predictions to be added to the new ones
		 */
			public void append_predictions(double previous_predictions [][],  double new_predictions [][], double shrink){
				
				if (previous_predictions.length==1){
					for (int i=0; i <previous_predictions[0].length; i++ ){
							previous_predictions[0][i]+=  new_predictions[i][0]*shrink;
					}
					
				}else {
				
					for (int i=0; i <previous_predictions[0].length; i++ ){
						for (int j=0; j <previous_predictions.length; j++ ){
							previous_predictions[j][i]+= new_predictions[i][j]*shrink;
							

						}	
						
				}
			}
			}



		/**
		 * 
		 * @param previous_predictions : Previous predictions 
		 * @param new_predictions : New predictions to be added to the new ones
		 */
			public void append_predictions_score(double previous_predictions [][],  double new_predictions [][], double shrink){
				
				if (new_predictions[0].length==1){		
					for (int i=0; i <previous_predictions.length; i++ ){
						for (int j=0; j <previous_predictions[0].length; j++ ){
								previous_predictions[i][0]+= new_predictions[i][0]*shrink;			
						} 
					}
					
				}else {
				
					for (int i=0; i <previous_predictions.length; i++ ){
						for (int j=0; j <previous_predictions[0].length; j++ ){
							previous_predictions[i][j]+= new_predictions[i][j]*shrink;

						}
						

			
					}
					
				}
			}
			/**
			 * 
			 * @param previous_predictions : Previous predictions 
			 * @param new_predictions : New predictions to be added to the new ones
			 */
				public void append_predictions_score(double previous_predictions [],  double new_predictions [], double shrink){
					
					if (new_predictions.length==1){		
							for (int j=0; j <previous_predictions.length; j++ ){
									previous_predictions[0]+= new_predictions[0]*shrink;			
							} 
						
						
					}else {
					
							for (int j=0; j <previous_predictions.length; j++ ){
								previous_predictions[j]+= new_predictions[j]*shrink;


						}
						
					}
				}	
			/**
			 * 
			 * @param previous_predictions : raw scores output to be transformed into probabilities
			 */
			public void scale_scores(double previous_predictions [][]){
				
				for (int i=0; i <previous_predictions.length; i++ ){
					double sum=0.0;

		            for (int j = 0; j < previous_predictions[0].length; j++) {
		            	sum += previous_predictions[i][j];
		            }

		            for (int j = 0; j <  previous_predictions[0].length; j++) {
		            	previous_predictions[i][j] /= sum;
		            }
		            
				}
				}

			/**
			 * 
			 * @param previous_predictions : raw scores output to be transformed into probabilities
			 */
			public int scale_scores(double previous_predictions []){
				
					double sum=0.0;
					double max=Double.MIN_VALUE;
					int cla=-1;
					for (int j = 0; j < previous_predictions.length; j++) {
						if (previous_predictions[j]>max ){
							max=previous_predictions[j];
							cla=j;
						}
					}
			        for (int j = 0; j < previous_predictions.length; j++) {
			        	previous_predictions[j] = Math.exp(previous_predictions[j] - max);
			        	sum += previous_predictions[j];
			        }

			        for (int j = 0; j <  previous_predictions.length; j++) {
			        	previous_predictions[j] /= sum;
			        }
			        
			        return cla;
				
				}	
			
			/**
			 * 
			 * @param array : Array of string parameters for the given estimators in one level
			 * @param number_of_classes : number of distinct classes of the target variable
			 * @param islastlevel : True if it is teh outputlevel
			 * @return total number of columns to output for the given stacker
			 */
			public int estimate_classes(String array [], int number_of_classes, boolean islastlevel){
				
				int no=0;
				int add=(number_of_classes<=2?1:number_of_classes);
				if (islastlevel && number_of_classes==2){
					add=2;
				}
				for (int k=0; k <array.length; k++ ){
					String x=array[k];
					if (x.contains("AdaboostForestRegressor") ||
							x.contains("DecisionTreeRegressor")	||
							x.contains("GradientBoostingForestRegressor")	||
							x.contains("RandomForestRegressor")	||				
							x.contains("multinnregressor")	||	
							x.contains("Vanilla2hnnregressor")	||
							x.contains("LSVR")	||
							x.contains("LinearRegression")	||
							x.contains("LibFmRegressor")	||
							x.contains("knnRegressor")	||
							x.contains("KernelmodelRegressor")
							) {
						no++;
					} else {
						no+=add;
					}
				}
				
					return no;
			}
			
			/**
			 * 
			 * @param preds : 2 dimensional predictions
			 * @param target : one dimensional target variable
			 * @return : the logloss metric
			 */
			public double logloss (double preds[][], double target []){
				double metr=0.0;
				double errorlog=0;
				double len=preds.length;
			    // Throw exception if the size is not 2
				if (preds[0].length==1){
					for (int i=0; i <preds.length; i++ ) {
						double value=preds[i][0];
						if (value>1.0-(1E-14)){
							value=1.0-(1E-14);
						} else if (value<0+(1E-14)){
							value=0.0+(1E-14);
						}
						if (target[i]==0){
							errorlog-=(1-target[i]) * Math.log(1-value);
						} else {
							errorlog-=target[i]*Math.log(value) ;
						}
					
					}
				} else {
					
					for (int i=0; i <preds.length; i++ ) {
						double value=preds[i][(int) (target[i]) ];
						if (value>1.0-(1E-14)){
							value=1.0-(1E-14);
						} else if (value<0+(1E-14)){
							value=0.0+(1E-14);
						}
							errorlog-=1.0*Math.log(value) ;
						
					
					}				
					
				}
				errorlog=errorlog/len;
				metr=errorlog;
				return metr;
			}
			
			/**
			 * 
			 * @param preds : 2 dimensional predictions
			 * @param target : one dimensional target variable
			 * @return : the accuracy metric
			 */
			public  double accuracy (double preds[][], double target []){
				double metr=0.0;
				double errorlog=0;
				double count_of_correct=0.0;
				double len=preds.length;
			    // Throw exception if the size is not 2
				if (preds[0].length==1){
					for (int i=0; i <preds.length; i++ ) {
						double value=preds[i][0];
						if (value>=0.5){
							value=1.0;
						} else {
							value=0.0;
						}
						if (target[i]==value){
							count_of_correct+=1.0;
						} 
					
					}
				} else {
					
					for (int i=0; i <preds.length; i++ ) {
						double maximum=0.0;
						double proba=preds[i][0];
						for (int j=1; j <preds[0].length;j++ ){
							if (preds[i][j]>proba){
								proba=preds[i][j];
								maximum=j;
							}
						}
						if (target[i]==maximum){
							count_of_correct+=1.0;
						} 
						
					
					}				
					
				}
				errorlog=count_of_correct/len;
				metr=errorlog;
				return metr;
			}
			/**
			 * 
			 * @param preds : 2 dimensional predictions
			 * @param target : one dimensional target variable
			 * @return : the rmse metric
			 */
			public  double rmse (double preds[][], double target []){
				double metr=0.0;
				double errorlog=0;
				double len=preds.length;
			    // Throw exception if the size is not 2
					for (int i=0; i <preds.length; i++ ) {
						double value=preds[i][0];
						errorlog=value-target[i];
						errorlog*=errorlog	;
						metr+=errorlog;
					
					}
				 
					metr=Math.sqrt(metr/len);
				return metr;
			}
			
			
			}




