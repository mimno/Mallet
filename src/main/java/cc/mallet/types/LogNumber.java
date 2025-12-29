package cc.mallet.types;

public class LogNumber {
  public  double logVal;
  public  boolean sign;
  
  public LogNumber(double logVal, boolean sign) {
    this.logVal = logVal;
    this.sign = sign;
  }
  
  public void timesEquals(LogNumber other) {
    this.logVal += other.logVal;
    this.sign = (this.sign && other.sign) || (!this.sign && !other.sign);
  }
  
  public double exp() {
    double exp = Math.exp(logVal);
    if (!sign) {
      exp *= -1;
    }
    return exp;
  }
  
  public void set(double logVal, boolean sign) {
    this.logVal = logVal;
    this.sign = sign;
  }
  
  public void plusEquals(LogNumber other) {
    if (this.logVal == Double.NEGATIVE_INFINITY) {
      // this.logVal may become negative infinity
      // without having the sign flipped to true.
      // if so, flip the sign now.
      if (!this.sign) {
        this.sign = true;
      }
      if (other.logVal == Double.NEGATIVE_INFINITY) {
        // this.logVal is already negative infinity,
        // so we do nothing
        return;
      }
      this.logVal = other.logVal;
      this.sign = other.sign;
    }
    else if (other.logVal == Double.NEGATIVE_INFINITY) {
      // no change
      return;
    }
    else if (this.logVal > other.logVal) {
      if (this.sign && other.sign) {
        this.logVal = this.logVal + Math.log (1 + Math.exp(other.logVal-this.logVal));
        // sign was already positive, no need to change
        //this.sign = true;
      }
      else if (this.sign && !other.sign) {
        this.logVal = this.logVal + Math.log (1 - Math.exp(other.logVal-this.logVal));
        // sign was already positive, no need to change
        //this.sign = true;
      }
      else if (!this.sign && other.sign) {
        this.logVal = this.logVal + Math.log (1 - Math.exp(other.logVal-this.logVal));
        // sign was already negative, no need to change
        //this.sign = false;
      }
      else if (!this.sign && !other.sign) {
        this.logVal = this.logVal + Math.log (1 + Math.exp(other.logVal-this.logVal));
        // sign was already negative, no need to change
        //this.sign = false;
      }
    }
    else {
      if (this.sign && other.sign) {
        this.logVal = other.logVal + Math.log (1 + Math.exp(this.logVal - other.logVal));
        // alpha sign was already positive, no need to change
        //signs[si] = true;
      }
      else if (this.sign && !other.sign) {
        this.logVal = other.logVal + Math.log (1 - Math.exp(this.logVal - other.logVal));
        // alpha was already negative, no need to change
        this.sign = false;
      }
      else if (!this.sign && other.sign) {
        this.logVal = other.logVal + Math.log (1 - Math.exp(this.logVal - other.logVal));
        // alpha sign was already positive, no need to change
        this.sign = true;
      }
      else if (!this.sign && !other.sign) {
        this.logVal = other.logVal + Math.log (1 + Math.exp(this.logVal - other.logVal));
        // alpha was already negative, no need to change
        //this.sign = false;
      }
    }
  }
  
  @Override 
  public String toString() {
    return "log value = " + this.logVal + ", positive = " + this.sign; 
  }
}
