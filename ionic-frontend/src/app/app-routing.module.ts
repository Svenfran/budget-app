import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  {
    path: '', redirectTo: 'domains/tabs/overview', pathMatch: 'full'
  },
  {
    path: 'domains',
    loadChildren: () => import('./domains/domains.module').then( m => m.DomainsPageModule)
  },
  {
    path: 'overview',
    loadChildren: () => import('./domains/overview/overview.module').then( m => m.OverviewPageModule)
  },
  {
    path: 'cartlist',
    loadChildren: () => import('./domains/cartlist/cartlist.module').then( m => m.CartlistPageModule)
  },
  {
    path: 'shoppinglist',
    loadChildren: () => import('./domains/shoppinglist/shoppinglist.module').then( m => m.ShoppinglistPageModule)
  },
  {
    path: 'groupoverview',
    loadChildren: () => import('./groupoverview/groupoverview.module').then( m => m.GroupoverviewPageModule)
  },
  {
    path: 'group-members',
    loadChildren: () => import('./group-members/group-members.module').then( m => m.GroupMembersPageModule)
  },
  {
    path: 'categoryoverview',
    loadChildren: () => import('./categoryoverview/categoryoverview.module').then( m => m.CategoryoverviewPageModule)
  },
  {
    path: 'settlement-payment',
    loadChildren: () => import('./settlement-payment/settlement-payment.module').then( m => m.SettlementPaymentPageModule)
  },
  {
    path: 'auth',
    loadChildren: () => import('./auth/auth.module').then( m => m.AuthPageModule)
  },
];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule { }
