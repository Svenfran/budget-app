import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal, WritableSignal } from '@angular/core';
import { environment } from 'src/environments/environment';
import { Cart } from '../model/cart';
import { Group } from 'src/app/model/group';
import { INIT_VALUES } from 'src/app/constants/default-values';
import { AlertService } from 'src/app/service/alert.service';
import { SettlementPaymentDto } from 'src/app/settlement-payment/model/settlement-payment-dto';
import { RECURRENCE_TYPE } from 'src/app/constants/recurrence-type';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { FileExportService } from 'src/app/service/file-export.service';
import { ExportOptions } from '../model/export-options';
import { exportFileName, exportMimeType } from './export-file';

@Injectable({
  providedIn: 'root'
})
export class CartService {

  private apiBaseUrl = environment.apiBaseUrl;
  private cartlistUrl = `${this.apiBaseUrl}/api/carts/carts-by-groupid`;
  private deleteCartUrl = `${this.apiBaseUrl}/api/carts/delete`;
  private addCartUrl = `${this.apiBaseUrl}/api/carts/add`;
  private updateCartUrl = `${this.apiBaseUrl}/api/carts/update`;
  private exportUrl = `${this.apiBaseUrl}/api/carts/export`;
  private settlementPaymentUrl = `${this.apiBaseUrl}/api/carts/settlement-payment/add`;
  
  public cartList: WritableSignal<Cart[]> = signal<Cart[]>([]);
  public initCartList: WritableSignal<Cart[]> = signal<Cart[]>([]);
  public sum = computed(() => this.cartList().reduce((s, c) => s + (+c.amount), 0));
  public count = computed(() => this.cartList().length);

  public cartUpdated = signal(0);
  
  constructor(
    private http: HttpClient,
    private alertService: AlertService,
    private translate: TranslateService,
    private fileExportService: FileExportService
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

          if (err.error.includes('not within membership period')) {
            const header = this.translate.instant("alerts.cart.invalid_membership.header");
            const message = this.translate.instant("alerts.cart.invalid_membership.message_new_edit");
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  updateCart(cart: Cart): void {
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

          if (err.error.includes('not within membership period')) {
            const header = this.translate.instant("alerts.cart.invalid_membership.header");
            const message = this.translate.instant("alerts.cart.invalid_membership.message_new_edit");
            this.alertService.showErrorAlert(header, message);
          }
        }
      })
  }

  deleteCart(cartId: number): void {
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
        }
      })
  }

  addSettlementPayment(payment: SettlementPaymentDto): void {
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

          if (err.error.includes('not within membership period')) {
            this.alertService.showErrorAlert(
              this.translate.instant("alerts.cart.invalid_membership.header"),
              this.translate.instant("alerts.cart.invalid_membership.message_settlementpayment", { userName: payment.member.userName })
            )
          }
        }
      })
  }

  /**
   * Ausgabenexport (DIV-68): lädt die Datei, speichert sie in „Dokumente“ und öffnet sie. Reiner Lesevorgang, ändert
   * keinen Signal-State. Fehler (HTTP oder Speichern) zeigen einen Alert.
   *
   * @returns true, wenn die Datei gespeichert wurde (auch wenn sie nicht geöffnet werden konnte)
   */
  async exportCarts(options: ExportOptions, group: Group): Promise<boolean> {
    if (options.scope === 'GROUP' && group.flag?.includes(INIT_VALUES.DEFAULT)) {
      return false;
    }
    const params: Record<string, string> = { format: options.format };
    if (options.scope === 'GROUP') {
      params['groupId'] = String(group.id);
    }
    const fileName = exportFileName(options, group.name, {
      prefix: this.translate.instant('domains.spendings.title'),
      allGroups: this.translate.instant('domains.spendings.export.filename_all_groups'),
    }, new Date());

    try {
      const blob = await firstValueFrom(this.http.get(this.exportUrl, { params, responseType: 'blob' }));
      const result = await this.fileExportService.saveAndOpen(blob, fileName, exportMimeType(options.format));
      this.alertService.showToast(this.translate.instant(result === 'opened'
        ? 'domains.spendings.toast.message'
        : 'domains.spendings.export.toast_saved_not_opened'));
      return true;
    } catch (err) {
      console.error('Error exporting carts:', err);
      this.alertService.showErrorAlert(
        this.translate.instant('alerts.export.header'),
        this.translate.instant('alerts.export.message'));
      return false;
    }
  }
}
