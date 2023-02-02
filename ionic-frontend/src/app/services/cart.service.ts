import { HttpClient, HttpEventType } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from 'src/environments/environment';
import { Filesystem, Directory } from '@capacitor/filesystem';
import { FileOpener } from '@ionic-native/file-opener/ngx';
import { Cart } from '../models/cart';
import { SettlementPaymentDto } from '../models/settlement-payment-dto';
import { ToastController } from '@ionic/angular';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  downloadProgress = 0;

  private apiBaseUrl = environment.apiBaseUrlExternal;
  private cartlistUrl = `${this.apiBaseUrl}/api/carts/carts-by-groupid`;
  private getCartByIdUrl = `${this.apiBaseUrl}/api/carts`;
  private addCartUrl = `${this.apiBaseUrl}/api/carts/add`;
  private updateCartUrl = `${this.apiBaseUrl}/api/carts/update`;
  private deleteCartUrl = `${this.apiBaseUrl}/api/carts/delete`;
  private settlementPaymentUrl = `${this.apiBaseUrl}/api/carts/settlement-payment/add`;
  private excelFileUrl = `${this.apiBaseUrl}/api/carts/download`;

  private _cartModified = new BehaviorSubject<boolean>(false);

  constructor(
    private http: HttpClient,
    private fileOpener: FileOpener,
    private toastCtrl: ToastController
  ) { }

  setCartModified(cartMod: boolean) {
    this._cartModified.next(cartMod);
  }

  get cartModified() {
    return this._cartModified.asObservable();
  }

  getCartListByGroupId(groupId: number): Observable<Cart[]> {
    return this.http.get<Cart[]>(`${this.cartlistUrl}/${groupId}`);
  }

  getCartById(cartId: number): Observable<Cart> {
    return this.http.get<Cart>(`${this.getCartByIdUrl}/${cartId}`);
  }

  addCart(cart: Cart): Observable<Cart> {
    return this.http.post<Cart>(this.addCartUrl, cart);
  }

  updateCart(cart: Cart): Observable<Cart> {
    return this.http.put<Cart>(this.updateCartUrl, cart);
  }

  deleteCart(cartId: number): Observable<void> {
    const deleteCartUrl = `${this.deleteCartUrl}/${cartId}`;
    return this.http.delete<void>(deleteCartUrl);
  }

  addSettlementPayment(payment: SettlementPaymentDto): Observable<SettlementPaymentDto> {
    return this.http.post<SettlementPaymentDto>(this.settlementPaymentUrl, payment);
  }

  getExcelFile(groupId: number, filename: string) {
    return this.http.get<any>(`${this.excelFileUrl}/${groupId}`, { 
      responseType: 'blob' as 'json',
      reportProgress: true,
      observe: 'events'
    }).subscribe(async event => {
      if (event.type === HttpEventType.DownloadProgress) {
        this.downloadProgress = Math.round((100 * event.loaded) / event.total);
      } else if (event.type === HttpEventType.Response) {
        this.downloadProgress = 0;
        const base64 = await this.convertBlobToBase64(event.body) as string;

        const savedFile = await Filesystem.writeFile({
          path: filename,
          data: base64,
          directory: Directory.Documents,
        });
        const path = savedFile.uri;
        const mimeType = this.getMimeType(name);
        let message = "Datei erfolgreich heruntergeladen."
        this.showToast(message)
        this.fileOpener.open(path, mimeType)
          .then(() => console.log('File is opended'))
          .catch(error => console.log('Error opening file', error));
        }
      });
    }

  private convertBlobToBase64(blob: Blob) {
    return new Promise ((resolve, reject) => {
      const reader = new FileReader;
      reader.onerror = reject;
      reader.onload = () => {
        resolve(reader.result);
      };
      reader.readAsDataURL(blob);
    })
  };

  private getMimeType(name) {
    if (name.indexOf('xlsx') >= 0) {
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    }
  }

  private showToast(message: string) {
    this.toastCtrl
      .create({
        message: message,
        duration: 1500,
        position: 'bottom'
      })
      .then(toastEl => toastEl.present());

  }

}
