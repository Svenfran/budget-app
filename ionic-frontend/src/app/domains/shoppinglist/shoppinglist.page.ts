import { Component, OnInit } from '@angular/core';
import { GroupService } from 'src/app/services/group.service';

@Component({
  selector: 'app-shoppinglist',
  templateUrl: './shoppinglist.page.html',
  styleUrls: ['./shoppinglist.page.scss'],
})
export class ShoppinglistPage implements OnInit {

  activeGroupName: string;

  constructor(
    private groupService: GroupService
  ) { }

  ngOnInit() {
    this.groupService.activeGroup.subscribe(group => {
      this.activeGroupName = group.name;
    })
  }

}
