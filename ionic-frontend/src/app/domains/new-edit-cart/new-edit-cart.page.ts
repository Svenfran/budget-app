import { DatePipe } from '@angular/common';
import { Component, Input, OnInit } from '@angular/core';
import { FormGroup, UntypedFormBuilder, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { LoadingController } from '@ionic/angular';
import { CategoryDto } from 'src/app/models/category';
import { first } from 'rxjs/operators';
import { CartService } from 'src/app/services/cart.service';
import { CategoryService } from 'src/app/services/category.service';
import { Cart } from 'src/app/models/cart';

@Component({
  selector: 'app-new-edit-cart',
  templateUrl: './new-edit-cart.page.html',
  styleUrls: ['./new-edit-cart.page.scss'],
})
export class NewEditCartPage implements OnInit {

  form: FormGroup;
  categories: CategoryDto[] = [];
  // categoryDto: CategoryDto;
  // cartDto: Cart;
  today = new Date();
  isAddMode: boolean;
  cartId: String;

  constructor(
    private categoryService: CategoryService,
    private fb: UntypedFormBuilder,
    private cartService: CartService,
    private router: Router,
    private datePipe: DatePipe,
    private loadingCtrl: LoadingController,
    private route: ActivatedRoute) { }

  ngOnInit() {
    this.form = this.fb.group({
      id:[''],
      title: ['',[ Validators.required ]],
      amount: ['',[ Validators.required, Validators.pattern('[+-]?([0-9]*[.])?[0-9]+')]],
      description: [''],
      datePurchased: ['',[ Validators.required, Validators.pattern('(0[1-9]|1[0-9]|2[0-9]|3[01]).(0[1-9]|1[012]).[0-9]{4}')]],
      category: ['',[ Validators.required ]]
    })

    this.cartId = this.route.snapshot.paramMap.get('id');
    this.isAddMode = !this.cartId;
 
    if (!this.isAddMode) {
      this.cartService.getCartById(+this.cartId)
      .pipe(first())
      .subscribe(data => 
        this.form.patchValue({
          id: data.id,
          title: data.title,
          amount: data.amount.toFixed(2),
          description: data.description,
          datePurchased: this.getStringFromDate(data.datePurchased),
          category: data.categoryDto.name
        })
      );
    } else if (this.isAddMode) {
      this.form.patchValue({
        datePurchased: this.getCurrentDateAsString()
      });
    }

    this.categoryService.getCategories().subscribe((categories) => {
      this.categories = categories;
    })
  }

  getCurrentDateAsString() {
    return this.datePipe.transform(this.today, 'dd.MM.yyyy');
  }

  getStringFromDate(date: Date) {
    return this.datePipe.transform(date, 'dd.MM.yyyy');
  }

  getDateFromString(date: string) {
    let dateArray = date.split('.');
    let newDateFormat = dateArray[2] + "-" + dateArray[1] + "-" + dateArray[0];
    return new Date(newDateFormat);
  }

  onCreateCart() {
    this.loadingCtrl.create({
      message: 'Füge Einkauf hinzu...'
    }).then(loadingEl => {
      loadingEl.present();
      let newCategory = new CategoryDto(
        this.categories.filter(cat => cat.name === this.form.value.category)[0].id,
        this.form.value.category
        );

      let newCart = new Cart(
        null,
        this.form.value.title,
        this.form.value.description,
        this.form.value.amount,
        this.getDateFromString(this.form.value.datePurchased),
        null,
        newCategory
      );
      this.cartService.addCart(newCart).subscribe(() => {
          loadingEl.dismiss();
          this.form.reset();
          this.router.navigate(['domains/tabs/cartlist']);
        },
        error => {
          console.log(error);
        }
      )
    })
  }

  onUpdateCart() {
    this.loadingCtrl.create({
      message: 'Bearbeite Einkauf...'
    }).then(loadingEl => {
      let updateCategory = new CategoryDto(
        this.categories.filter(cat => cat.name === this.form.value.category)[0].id,
        this.form.value.category
        );
  
      let updateCart = new Cart(
        +this.cartId,
        this.form.value.title,
        this.form.value.description,
        this.form.value.amount,
        this.getDateFromString(this.form.value.datePurchased),
        null,
        updateCategory
      );
      this.cartService.updateCart(updateCart).subscribe(() => {
        loadingEl.dismiss();
        this.form.reset();
        this.router.navigate(['domains/tabs/cartlist']);
      })
    })
  }

  onSubmit() {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.isAddMode) {
      this.onCreateCart();
    } else {
      this.onUpdateCart();
    }
  }


  get title() {return this.form.get('title')}
  get description() {return this.form.get('description');}
  get amount() {return this.form.get('amount');}
  get datePurchased() {return this.form.get('datePurchased');}
  get category() {return this.form.get('category');}

}