package com.github.svenfran.budgetapp.budgetappbackend.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class Translator {

    private final MessageSource messageSource;

    @Autowired
    public Translator(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String translate(String key, Object... args) {
        var locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, key, locale);
    }

}
