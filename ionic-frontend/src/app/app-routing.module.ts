import { NgModule } from '@angular/core';
import { PreloadAllModules, RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  {
    path: '',
    redirectTo: 'domains/tabs/overview',
    pathMatch: 'full'
  },
  {
    path: 'auth',
    loadChildren: () => import('./auth/auth.module').then( m => m.AuthPageModule)
  },
  {
    path: 'domains',
    loadChildren: () => import('./domains/domains.module').then( m => m.DomainsPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'overview',
    loadChildren: () => import('./domains/overview/overview.module').then( m => m.OverviewPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'cartlist',
    loadChildren: () => import('./domains/cartlist/cartlist.module').then( m => m.CartlistPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'shoppinglist',
    loadChildren: () => import('./domains/shoppinglist/shoppinglist.module').then( m => m.ShoppinglistPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'groupoverview',
    loadChildren: () => import('./groupoverview/groupoverview.module').then( m => m.GroupoverviewPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'groupmembers',
    loadChildren: () => import('./groupmembers/groupmembers.module').then( m => m.GroupmembersPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'categoryoverview',
    loadChildren: () => import('./categoryoverview/categoryoverview.module').then( m => m.CategoryoverviewPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'settlement-payment',
    loadChildren: () => import('./settlement-payment/settlement-payment.module').then( m => m.SettlementPaymentPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'filter-modal',
    loadChildren: () => import('./filter-modal/filter-modal.module').then( m => m.FilterModalPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'userprofile',
    loadChildren: () => import('./userprofile/userprofile.module').then( m => m.UserprofilePageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'password-change',
    loadChildren: () => import('./password-change/password-change.module').then( m => m.PasswordChangePageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'no-group',
    loadChildren: () => import('./error-page/no-group/no-group.module').then( m => m.NoGroupPageModule),
    canLoad: [AuthGuard]
  },
  {
    path: 'server-unavailable',
    loadChildren: () => import('./error-page/server-unavailable/server-unavailable.module').then( m => m.ServerUnavailablePageModule),
    canLoad: [AuthGuard]
  }

];

@NgModule({
  imports: [
    RouterModule.forRoot(routes, { preloadingStrategy: PreloadAllModules })
  ],
  exports: [RouterModule]
})
export class AppRoutingModule { }
