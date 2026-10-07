export class EmailValidator {
    static isNotValid(email: string){
      let pattern = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;
      let result = pattern.test(email);
      
      if (!result) {
        return {
          'email:validation:fail' : true
        }
      }
      return null;
    }
  }