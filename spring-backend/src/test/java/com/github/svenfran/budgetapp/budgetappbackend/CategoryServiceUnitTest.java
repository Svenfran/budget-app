package com.github.svenfran.budgetapp.budgetappbackend;

import com.github.svenfran.budgetapp.budgetappbackend.dto.CategoryDto;
import com.github.svenfran.budgetapp.budgetappbackend.entity.Category;
import com.github.svenfran.budgetapp.budgetappbackend.service.CategoryService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class CategoryServiceUnitTest {

    @Autowired
    private CategoryService categoryService;

    @Test
    public void h2InitialRecordsCreationOnApplicationStartTest() {
        List<CategoryDto> categories = categoryService.getAllCategories();
        Assert.assertEquals(categories.size(), 6);
    }
}
