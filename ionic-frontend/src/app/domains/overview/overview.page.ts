import { Component, OnInit } from '@angular/core';
import { GroupService } from 'src/app/services/group.service';

@Component({
  selector: 'app-overview',
  templateUrl: './overview.page.html',
  styleUrls: ['./overview.page.scss'],
})
export class OverviewPage implements OnInit {

  activeGroupName: string;
  userName: string;

  constructor(
    private groupService: GroupService
  ) { }

  ngOnInit() {
    this.getCurrentUser();
    this.groupService.activeGroup.subscribe(group => {
      if (group) {
        this.activeGroupName = group.name;
      }
    })
  }

  getCurrentUser() {
    this.groupService.currentUser.subscribe(user => {
      this.userName = user.userName;
    })
  }

}
