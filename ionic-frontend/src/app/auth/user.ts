export class User {
    constructor(
        public id: number,
        public name: string,
        public expirationDate: number,
        private _token: string
      ) {}
    
      get token() {
        if (!this.expirationDate || this.expirationDate <= new Date().getTime()) {
          return null;
        }
        return this._token;
      }

      get tokenDuration() {
        if (!this.token) {
          return 0;
        }
        return this.expirationDate - new Date().getTime();
      }
}
