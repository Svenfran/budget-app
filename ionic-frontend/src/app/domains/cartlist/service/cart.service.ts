import { HttpClient, HttpEventType } from '@angular/common/http';
import { computed, Injectable, signal, WritableSignal } from '@angular/core';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { environment } from 'src/environments/environment';
import { Cart } from '../model/cart';
import { Group } from 'src/app/model/group';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { AlertService } from 'src/app/service/alert.service';
import { FileOpener, FileOpenerOptions } from '@capacitor-community/file-opener';
import { SettlementPaymentDto } from 'src/app/settlement-payment/model/settlement-payment-dto';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { TranslateService } from '@ngx-translate/core';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  downloadProgress = 0;

  private apiBaseUrl = environment.apiBaseUrl;
  private cartlistUrl = `${this.apiBaseUrl}/api/carts/carts-by-groupid`;
  private deleteCartUrl = `${this.apiBaseUrl}/api/carts/delete`;
  private addCartUrl = `${this.apiBaseUrl}/api/carts/add`;
  private updateCartUrl = `${this.apiBaseUrl}/api/carts/update`;
  private excelFileUrl = `${this.apiBaseUrl}/api/carts/download`;
  private settlementPaymentUrl = `${this.apiBaseUrl}/api/carts/settlement-payment/add`;
  
  public cartList: WritableSignal<Cart[]> = signal<Cart[]>([]);
  public initCartList: WritableSignal<Cart[]> = signal<Cart[]>([]);
  public sum = computed(() => this.cartList().reduce((s, c) => s + (+c.amount), 0));
  public count = computed(() => this.cartList().length);

  public cartUpdated = signal(0);
  
  constructor(
    private http: HttpClient,
    private alertService: AlertService,
    private translate: TranslateService
  ) {}

  triggerUpdate() {
    this.cartUpdated.set(this.cartUpdated() + 1)
  }

  getCartListByGroupId(group: Group, refresh?: boolean, onComplete?: () => void): void {
    if (group.flag?.includes(INIT_VALUES.DEFAULT)) {
      this.cartList.set([]);
      this.initCartList.set([]);
      onComplete?.();
      return;
    };

    this.http
      .get<Cart[]>(`${this.cartlistUrl}/${group.id}`)
      .subscribe({
        next: (result) => {
          this.cartList.set(result || []);
          this.initCartList.set(result || []);
          onComplete?.();
          if (refresh) this.triggerUpdate();
        },
        error: (err) => {
          console.error('Error fetching cartlist:', err);
          this.cartList.set([]);
          this.initCartList.set([]);
          onComplete?.();
        }
      });
  }

  addCart(cart: Cart): void {
    const currentCartList = this.cartList();
    const currentInitCartList = this.initCartList();
    this.http
      .post<Cart>(this.addCartUrl, cart)
      .subscribe({
        next: (result) => {
          const newCart: Cart = {
            id: result.id,
            title: result.title,
            description: result.description,
            amount: result.amount,
            datePurchased: result.datePurchased,
            groupId: result.groupId,
            userDto: result.userDto,
            categoryDto: result.categoryDto,
            deleted: result.deleted,
            recurrenceType: result.recurrenceType,
            hasActiveTemplate: result.hasActiveTemplate,
            nextExecutionDate: result.nextExecutionDate
          }

          this.cartList.update(carts => {
            const updatedCarts = [...carts, newCart];
            return updatedCarts.sort((a, b) => new Date(b.datePurchased).getTime() - new Date(a.datePurchased).getTime());
          });

          this.initCartList.update(carts => {
            const updatedCarts = [...carts, newCart];
            return updatedCarts.sort((a, b) => new Date(b.datePurchased).getTime() - new Date(a.datePurchased).getTime());
          });

          this.triggerUpdate();
        },
        error: (err) => {
          console.error('Error adding cart:', err);
          this.cartList.set(currentCartList);
          this.initCartList.set(currentInitCartList);

          if (err.error.includes('not within membership period')) {
            const header = this.translate.instant("alerts.cart.invalid_membership.header");
            const message = this.translate.instant("alerts.cart.invalid_membership.message_new_edit");
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  updateCart(cart: Cart): void {
    const currentCartList = this.cartList();
    const currentInitCartList = this.initCartList();
    this.http
      .put<Cart>(this.updateCartUrl, cart)
      .subscribe({
        next: (result) => {
          const updatedCart: Cart = {
            id: result.id,
            title: result.title,
            description: result.description,
            amount: result.amount,
            datePurchased: result.datePurchased,
            groupId: result.groupId,
            userDto: result.userDto,
            categoryDto: result.categoryDto,
            deleted: result.deleted,
            recurrenceType: result.recurrenceType,
            hasActiveTemplate: result.hasActiveTemplate,
            nextExecutionDate: result.nextExecutionDate,
            templateUpdateSelected: cart.templateUpdateSelected,
            templateId: result.templateId
          }

          this.cartList.update(carts => {
            const oldCart = carts.find(c => c.id === result.id);

            const recurrenceTypeChanged =
              oldCart && oldCart.recurrenceType !== result.recurrenceType;

            let updatedCarts = carts.map(c => {
              if (c.id === result.id) {
                // das geänderte Cart updaten
                return { ...c, ...updatedCart };
              }
              
              if ((recurrenceTypeChanged || updatedCart?.templateUpdateSelected) && result.hasTemplateChanged &&
                  c.templateId && c.templateId === oldCart?.templateId) {
                return { ...c, recurrenceType: RECURRENCE_TYPE.NONE, hasActiveTemplate: false};
              }

              return c;
            });

            return updatedCarts.sort(
              (a, b) => new Date(b.datePurchased).getTime() - new Date(a.datePurchased).getTime()
            );
          });

          this.initCartList.update(carts => {
            const oldCart = carts.find(c => c.id === result.id);

            const recurrenceTypeChanged =
              oldCart && oldCart.recurrenceType !== result.recurrenceType;

            let updatedCarts = carts.map(c => {
              if (c.id === result.id) {
                // das geänderte Cart updaten
                return { ...c, ...updatedCart };
              }

              if ((recurrenceTypeChanged || updatedCart?.templateUpdateSelected) && result.hasTemplateChanged && 
                  c.templateId && c.templateId === oldCart?.templateId) {
                return { ...c, recurrenceType: RECURRENCE_TYPE.NONE, hasActiveTemplate: false};
              }

              return c;
            });

            return updatedCarts.sort(
              (a, b) => new Date(b.datePurchased).getTime() - new Date(a.datePurchased).getTime()
            );
          });

          this.triggerUpdate();
        },
        error: (err) => {
          console.error('Error updating cart:', err);
          this.cartList.set(currentCartList);
          this.initCartList.set(currentInitCartList);

          if (err.error.includes('not within membership period')) {
            const header = this.translate.instant("alerts.cart.invalid_membership.header");
            const message = this.translate.instant("alerts.cart.invalid_membership.message_new_edit");
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  deleteCart(cartId: number): void {
    const currentCartList = this.cartList();
    const currentInitCartList = this.initCartList();
    this.http
      .delete<void>(`${this.deleteCartUrl}/${cartId}`)
      .subscribe({
        next: () => {
          this.cartList.update(carts => carts.filter(cart => 
            cart.id !== cartId
          ));

          this.initCartList.update(carts => carts.filter(cart => 
            cart.id !== cartId
          ));

          this.triggerUpdate();
        },
        error: (err) => {
          console.error('Error deleting cart:', err);
          this.cartList.set(currentCartList);
          this.initCartList.set(currentInitCartList);
        }
      })
  }

  addSettlementPayment(payment: SettlementPaymentDto): void {
    const currentCartList = this.cartList();
    const currentInitCartList = this.initCartList();
    this.http
      .post<Cart[]>(this.settlementPaymentUrl, payment)
      .subscribe({
        next: (result) => {
          this.cartList.update(carts => {
            const updatedCarts = [...carts, ...result];
            return updatedCarts.sort((a, b) => new Date(b.datePurchased).getTime() - new Date(a.datePurchased).getTime());
          });
          
          this.initCartList.update(carts => {
            const updatedCarts = [...carts, ...result];
            return updatedCarts.sort((a, b) => new Date(b.datePurchased).getTime() - new Date(a.datePurchased).getTime());
          });
          
          this.triggerUpdate();
        },
        error: (err) => {
          console.error("Error adding settlement payment:", err);
          this.cartList.set(currentCartList);
          this.initCartList.set(currentInitCartList);

          if (err.error.includes('not within membership period')) {
            this.alertService.showErrorAlert(
              this.translate.instant("alerts.cart.invalid_membership.header"),
              this.translate.instant("alerts.cart.invalid_membership.message_settlementpayment", { userName: payment.member.userName })
            )
          }
        }
      })
  }

  getExcelFile(group: Group, fileName: string) {
    this.http
      .get<any>(`${this.excelFileUrl}/${group.id}`, {
        responseType: 'blob' as 'json',
        reportProgress: true,
        observe: 'events'
      })
      .subscribe({
        next: async (event) => {
          if (event.type === HttpEventType.DownloadProgress) {
            this.downloadProgress = event.total ? Math.round((100 * event.loaded) / event.total) : 0;
          } else if (event.type === HttpEventType.Response) {
            this.downloadProgress = 0;
            const base64 = await this.convertBlobToBase64(event.body) as string;
    
            const savedFile = await Filesystem.writeFile({
              path: fileName,
              data: base64,
              directory: Directory.Documents,
            });
            const path = savedFile.uri;
            const mimeType = this.getMimeType(fileName);
            let message = this.translate.instant('domains.spendings.toast.message');
            this.alertService.showToast(message);
            
            try {
              const fileOpenerOptions: FileOpenerOptions = {
                filePath: path,
                contentType: mimeType,
                openWithDefault: true,
              };
              await FileOpener.open(fileOpenerOptions);
            } catch (e) {
              console.log('Error opening file', e);
            }
          }
        },
        error: (err) => {
          console.error("Error downloading excel file:", err);
        }
      })
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

  private getMimeType(name: string): string {
    if (name.indexOf('xlsx') >= 0) {
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    }
    return 'application/octet-stream'; // default MIME type
  }
}
